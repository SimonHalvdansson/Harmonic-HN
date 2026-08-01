package com.simon.harmonichackernews.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;
import androidx.window.WindowSdkExtensions;
import androidx.window.embedding.ActivityEmbeddingController;
import androidx.window.embedding.DividerAttributes;
import androidx.window.embedding.EmbeddingRule;
import androidx.window.embedding.RuleController;
import androidx.window.embedding.SplitAttributes;
import androidx.window.embedding.SplitAttributesCalculatorParams;
import androidx.window.embedding.SplitController;
import androidx.window.embedding.SplitController.SplitSupportStatus;
import androidx.window.embedding.SplitPairRule;
import androidx.window.embedding.SplitPlaceholderRule;

import com.simon.harmonichackernews.R;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import kotlin.jvm.functions.Function1;

public class FoldableSplitInitializer implements Initializer<RuleController> {
   private static final String TAG = "FoldableSplitInit";

   /** Window SDK extension version required to set a divider between the two panes. */
   private static final int DIVIDER_EXTENSION_VERSION = 6;
   /** Window SDK extension version required to use a split attributes calculator. */
   private static final int CALCULATOR_EXTENSION_VERSION = 2;
   /** Window SDK extension version required to refresh already visible splits. */
   private static final int INVALIDATE_EXTENSION_VERSION = 3;

   private static RuleController ruleController;
   private static boolean calculatorInstalled = false;
   private static int appliedSplitPaneRatio = SettingsUtils.SPLIT_PANE_RATIO_UNSET;

   @NonNull
   @Override
   public RuleController create(@NonNull Context context) {
      ruleController = RuleController.getInstance(context);
      applyRules(context);

      return ruleController;
   }

   @NonNull
   @Override
   public List<Class<? extends Initializer<?>>> dependencies() {
      return Collections.emptyList();
   }

   public static boolean isFoldableSplitEnabled(Context context) {
      return isSplitSupported(context) && isFoldableDevice(context);
   }

   private static boolean isSplitSupported(Context context) {
      return SplitController.getInstance(context).getSplitSupportStatus().equals(SplitSupportStatus.SPLIT_AVAILABLE);
   }

   @SuppressLint("InlinedApi")
   private static boolean isFoldableDevice(Context context) {
      return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE);
   }

   /**
    * Applies the split rules using the currently configured split ratio. Safe to call again
    * whenever the ratio changes, in which case already visible splits are updated as well.
    */
   public static void applyRules(Context context) {
      Context appContext = context.getApplicationContext();
      RuleController controller = RuleController.getInstance(appContext);

      if (!isFoldableSplitEnabled(appContext)) {
         controller.clearRules();
         return;
      }

      appliedSplitPaneRatio = SettingsUtils.getSplitPaneRatio(appContext);
      controller.setRules(buildRules(appContext));
      installSplitAttributesCalculator(appContext);
      invalidateVisibleSplits(appContext);
   }

   /**
    * Reapplies the rules if the split ratio has changed since they were last applied, for instance
    * because it was changed in settings while a split was showing behind them.
    */
   public static void applyRulesIfSplitPaneRatioChanged(Context context) {
      if (!isFoldableSplitEnabled(context)
              || SettingsUtils.getSplitPaneRatio(context) == appliedSplitPaneRatio) {
         return;
      }

      applyRules(context);
   }

   private static Set<EmbeddingRule> buildRules(Context context) {
      SplitAttributes splitAttributes = createSplitAttributes(context);
      Set<EmbeddingRule> rules = new LinkedHashSet<>();

      for (EmbeddingRule rule : RuleController.parseRules(context, R.xml.main_split_config)) {
         if (rule instanceof SplitPairRule) {
            rules.add(withSplitAttributes((SplitPairRule) rule, splitAttributes));
         } else if (rule instanceof SplitPlaceholderRule) {
            rules.add(withSplitAttributes((SplitPlaceholderRule) rule, splitAttributes));
         } else {
            rules.add(rule);
         }
      }

      return rules;
   }

   private static SplitPairRule withSplitAttributes(SplitPairRule rule, SplitAttributes splitAttributes) {
      return new SplitPairRule.Builder(rule.getFilters())
              .setMinWidthDp(rule.getMinWidthDp())
              .setMinHeightDp(rule.getMinHeightDp())
              .setMinSmallestWidthDp(rule.getMinSmallestWidthDp())
              .setMaxAspectRatioInPortrait(rule.getMaxAspectRatioInPortrait())
              .setMaxAspectRatioInLandscape(rule.getMaxAspectRatioInLandscape())
              .setFinishPrimaryWithSecondary(rule.getFinishPrimaryWithSecondary())
              .setFinishSecondaryWithPrimary(rule.getFinishSecondaryWithPrimary())
              .setClearTop(rule.getClearTop())
              .setDefaultSplitAttributes(splitAttributes)
              .setTag(rule.getTag())
              .build();
   }

   private static SplitPlaceholderRule withSplitAttributes(SplitPlaceholderRule rule, SplitAttributes splitAttributes) {
      return new SplitPlaceholderRule.Builder(rule.getFilters(), rule.getPlaceholderIntent())
              .setMinWidthDp(rule.getMinWidthDp())
              .setMinHeightDp(rule.getMinHeightDp())
              .setMinSmallestWidthDp(rule.getMinSmallestWidthDp())
              .setMaxAspectRatioInPortrait(rule.getMaxAspectRatioInPortrait())
              .setMaxAspectRatioInLandscape(rule.getMaxAspectRatioInLandscape())
              .setSticky(rule.isSticky())
              .setFinishPrimaryWithPlaceholder(rule.getFinishPrimaryWithPlaceholder())
              .setDefaultSplitAttributes(splitAttributes)
              .setTag(rule.getTag())
              .build();
   }

   private static SplitAttributes createSplitAttributes(Context context) {
      SplitAttributes.Builder builder = new SplitAttributes.Builder()
              .setSplitType(SplitAttributes.SplitType.ratio(SettingsUtils.getSplitPaneRatio(context) / 100f))
              .setLayoutDirection(SplitAttributes.LayoutDirection.LOCALE);

      if (getExtensionVersion() >= DIVIDER_EXTENSION_VERSION) {
         builder.setDividerAttributes(new DividerAttributes.DraggableDividerAttributes.Builder()
                 .setWidthDp(DividerAttributes.WIDTH_SYSTEM_DEFAULT)
                 .setDragRange(new DividerAttributes.DragRange.SplitRatioDragRange(
                         SettingsUtils.MIN_SPLIT_PANE_RATIO / 100f,
                         SettingsUtils.MAX_SPLIT_PANE_RATIO / 100f))
                 .build());
      }

      return builder.build();
   }

   /**
    * Lets the window manager ask us for the split attributes every time it re-evaluates a split,
    * so that a changed ratio is picked up without having to recreate the split.
    */
   private static void installSplitAttributesCalculator(Context context) {
      if (calculatorInstalled || getExtensionVersion() < CALCULATOR_EXTENSION_VERSION) {
         return;
      }

      final Context appContext = context.getApplicationContext();
      SplitController.getInstance(appContext).setSplitAttributesCalculator(
              new Function1<SplitAttributesCalculatorParams, SplitAttributes>() {
                 @Override
                 public SplitAttributes invoke(SplitAttributesCalculatorParams params) {
                    if (!params.areDefaultConstraintsSatisfied()) {
                       return new SplitAttributes.Builder()
                               .setSplitType(SplitAttributes.SplitType.SPLIT_TYPE_EXPAND)
                               .build();
                    }

                    return createSplitAttributes(appContext);
                 }
              });
      calculatorInstalled = true;
   }

   private static void invalidateVisibleSplits(Context context) {
      if (!calculatorInstalled || getExtensionVersion() < INVALIDATE_EXTENSION_VERSION) {
         return;
      }

      try {
         ActivityEmbeddingController.getInstance(context).invalidateVisibleActivityStacks();
      } catch (UnsupportedOperationException e) {
         Log.w(TAG, "Could not refresh visible splits", e);
      }
   }

   private static int getExtensionVersion() {
      return WindowSdkExtensions.getInstance().getExtensionVersion();
   }
}
