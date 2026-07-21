package com.simon.harmonichackernews;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.activity.ComponentActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.simon.harmonichackernews.utils.ThemeUtils;

import java.util.Random;

/** Full-screen Ginibre log-gas Easter egg opened from the debug settings. */
public class CoulombGasActivity extends ComponentActivity {

    private CoulombGasView gasView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.setupTheme(this, false);

        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        gasView = new CoulombGasView(this, isLightTheme());
        setContentView(gasView);
        hideSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        gasView.setLightTheme(isLightTheme());
        hideSystemBars();
    }

    private boolean isLightTheme() {
        return ThemeUtils.isLightMode(this);
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    private static final class CoulombGasView extends SurfaceView
            implements SurfaceHolder.Callback, Runnable {

        private static final int PARTICLE_COUNT = 1500;
        private static final int COLOR_STEPS = 96;
        private static final int MAX_TOUCH_CHARGES = 32;

        // Deliberately accelerated so the initial gas settles quickly on a phone screen.
        private static final float TIME_STEP = 75f / 30000f;
        private static final float VELOCITY_DAMPING = 0.8f;
        private static final float SOFTENING = 1e-6f;
        private static final float WORLD_TO_VIEW = 0.35f;
        private static final float TOUCH_CHARGE = 1000f;

        private final SurfaceHolder surfaceHolder;
        private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] x = new float[PARTICLE_COUNT];
        private final float[] y = new float[PARTICLE_COUNT];
        private final float[] velocityX = new float[PARTICLE_COUNT];
        private final float[] velocityY = new float[PARTICLE_COUNT];
        private final float[] accelerationX = new float[PARTICLE_COUNT];
        private final float[] accelerationY = new float[PARTICLE_COUNT];
        private final int[] speedColors = new int[COLOR_STEPS];
        private final Object touchLock = new Object();
        private final float[] touchChargeX = new float[MAX_TOUCH_CHARGES];
        private final float[] touchChargeY = new float[MAX_TOUCH_CHARGES];
        private final float[] simulationTouchChargeX = new float[MAX_TOUCH_CHARGES];
        private final float[] simulationTouchChargeY = new float[MAX_TOUCH_CHARGES];
        private final float density;

        private volatile boolean running;
        private volatile boolean lightTheme;
        private int touchChargeCount;
        private Thread renderThread;
        private float speedColorScale = 1f;
        private boolean particlesInitialized;

        CoulombGasView(Context context, boolean lightTheme) {
            super(context);
            this.lightTheme = lightTheme;
            density = getResources().getDisplayMetrics().density;
            surfaceHolder = getHolder();
            surfaceHolder.addCallback(this);
            setFocusable(true);
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            updatePalette();
        }

        void setLightTheme(boolean lightTheme) {
            this.lightTheme = lightTheme;
            updatePalette();
        }

        private void initializeParticles(int width, int height) {
            Random random = new Random();
            float scale = Math.min(width, height) * WORLD_TO_VIEW;
            float worldHalfWidth = width * 0.5f / scale;
            float worldHalfHeight = height * 0.5f / scale;
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                x[i] = (random.nextFloat() * 2f - 1f) * worldHalfWidth;
                y[i] = (random.nextFloat() * 2f - 1f) * worldHalfHeight;
            }
            particlesInitialized = true;
        }

        private void updatePalette() {
            float brightness = lightTheme ? 0.78f : 1f;
            for (int i = 0; i < COLOR_STEPS; i++) {
                float t = i / (float) (COLOR_STEPS - 1);
                float hue = 225f * (1f - t);
                speedColors[i] = Color.HSVToColor(
                        new float[]{hue, 0.88f, brightness * t});
            }
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            // Rendering starts after surfaceChanged supplies the real fullscreen dimensions.
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                int height) {
            if (!particlesInitialized && width > 0 && height > 0) {
                initializeParticles(width, height);
            }
            startRendering();
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            stopRendering();
        }

        private synchronized void startRendering() {
            if (running) {
                return;
            }
            running = true;
            renderThread = new Thread(this, "CoulombGas");
            renderThread.start();
        }

        private synchronized void stopRendering() {
            running = false;
            Thread thread = renderThread;
            renderThread = null;
            if (thread != null) {
                thread.interrupt();
                try {
                    thread.join(500L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void run() {
            final long frameDurationNanos = 1_000_000_000L / 60L;
            while (running) {
                long frameStart = System.nanoTime();
                simulateStep();
                drawFrame();

                long remainingNanos = frameDurationNanos - (System.nanoTime() - frameStart);
                if (remainingNanos > 0L) {
                    SystemClock.sleep(remainingNanos / 1_000_000L);
                }
            }
        }

        private void simulateStep() {
            final float confinement = -2f * PARTICLE_COUNT;
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                accelerationX[i] = confinement * x[i];
                accelerationY[i] = confinement * y[i];
            }

            for (int i = 0; i < PARTICLE_COUNT - 1; i++) {
                float xi = x[i];
                float yi = y[i];
                for (int j = i + 1; j < PARTICLE_COUNT; j++) {
                    float dx = xi - x[j];
                    float dy = yi - y[j];
                    float scale = 2f / (dx * dx + dy * dy + SOFTENING);
                    float forceX = dx * scale;
                    float forceY = dy * scale;
                    accelerationX[i] += forceX;
                    accelerationY[i] += forceY;
                    accelerationX[j] -= forceX;
                    accelerationY[j] -= forceY;
                }
            }

            int activeChargeCount;
            synchronized (touchLock) {
                activeChargeCount = touchChargeCount;
                System.arraycopy(touchChargeX, 0, simulationTouchChargeX, 0,
                        activeChargeCount);
                System.arraycopy(touchChargeY, 0, simulationTouchChargeY, 0,
                        activeChargeCount);
            }
            for (int charge = 0; charge < activeChargeCount; charge++) {
                float chargeX = simulationTouchChargeX[charge];
                float chargeY = simulationTouchChargeY[charge];
                for (int i = 0; i < PARTICLE_COUNT; i++) {
                    float dx = x[i] - chargeX;
                    float dy = y[i] - chargeY;
                    float scale = TOUCH_CHARGE / (dx * dx + dy * dy + SOFTENING);
                    accelerationX[i] += dx * scale;
                    accelerationY[i] += dy * scale;
                }
            }

            for (int i = 0; i < PARTICLE_COUNT; i++) {
                float vx = velocityX[i] + accelerationX[i] * TIME_STEP;
                float vy = velocityY[i] + accelerationY[i] * TIME_STEP;
                x[i] += vx * TIME_STEP;
                y[i] += vy * TIME_STEP;
                velocityX[i] = vx * VELOCITY_DAMPING;
                velocityY[i] = vy * VELOCITY_DAMPING;
            }
        }

        private void drawFrame() {
            Canvas canvas = null;
            try {
                canvas = surfaceHolder.lockHardwareCanvas();
                if (canvas == null) {
                    return;
                }

                int background = lightTheme ? Color.WHITE : Color.BLACK;
                canvas.drawColor(background);

                float centerX = canvas.getWidth() * 0.5f;
                float centerY = canvas.getHeight() * 0.5f;
                float scale = Math.min(canvas.getWidth(), canvas.getHeight()) * WORLD_TO_VIEW;
                float particleRadius = Math.max(2f, 1.4f * density);

                float speedSquareSum = 0f;
                for (int i = 0; i < PARTICLE_COUNT; i++) {
                    speedSquareSum += velocityX[i] * velocityX[i]
                            + velocityY[i] * velocityY[i];
                }
                float targetScale = Math.max(0.05f,
                        2f * (float) Math.sqrt(speedSquareSum / PARTICLE_COUNT));
                speedColorScale += (targetScale - speedColorScale) * 0.04f;

                for (int i = 0; i < PARTICLE_COUNT; i++) {
                    float speed = (float) Math.sqrt(velocityX[i] * velocityX[i]
                            + velocityY[i] * velocityY[i]);
                    float normalizedSpeed = Math.min(1f, speed / speedColorScale);
                    int colorIndex = Math.min(COLOR_STEPS - 1,
                            (int) (normalizedSpeed * (COLOR_STEPS - 1)));
                    particlePaint.setColor(speedColors[colorIndex]);
                    canvas.drawCircle(centerX + x[i] * scale, centerY - y[i] * scale,
                            particleRadius, particlePaint);
                }
            } catch (IllegalArgumentException ignored) {
                // The surface can disappear between the lifecycle check and the lock call.
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    } catch (IllegalArgumentException ignored) {
                        // Surface destruction can race with posting the finished frame.
                    }
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_MOVE:
                    updateTouchCharges(event, -1);
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    updateTouchCharges(event, event.getActionIndex());
                    return true;
                case MotionEvent.ACTION_UP:
                    clearTouchCharges();
                    performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    clearTouchCharges();
                    return true;
                default:
                    return true;
            }
        }

        private void updateTouchCharges(MotionEvent event, int pointerIndexToSkip) {
            float scale = Math.min(getWidth(), getHeight()) * WORLD_TO_VIEW;
            if (scale <= 0f) {
                return;
            }
            synchronized (touchLock) {
                int chargeIndex = 0;
                for (int pointerIndex = 0;
                        pointerIndex < event.getPointerCount()
                                && chargeIndex < MAX_TOUCH_CHARGES;
                        pointerIndex++) {
                    if (pointerIndex == pointerIndexToSkip) {
                        continue;
                    }
                    touchChargeX[chargeIndex] =
                            (event.getX(pointerIndex) - getWidth() * 0.5f) / scale;
                    touchChargeY[chargeIndex] =
                            (getHeight() * 0.5f - event.getY(pointerIndex)) / scale;
                    chargeIndex++;
                }
                touchChargeCount = chargeIndex;
            }
        }

        private void clearTouchCharges() {
            synchronized (touchLock) {
                touchChargeCount = 0;
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
