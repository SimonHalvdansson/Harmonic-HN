package com.simon.harmonichackernews.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;
import androidx.window.embedding.RuleController;
import androidx.window.embedding.SplitController;
import androidx.window.embedding.SplitController.SplitSupportStatus;

import com.simon.harmonichackernews.R;

import java.util.Collections;
import java.util.List;

public class FoldableSplitInitializer implements Initializer<RuleController> {
   private static RuleController ruleController;

   @NonNull
   @Override
   public RuleController create(@NonNull Context context) {
      ruleController = RuleController.getInstance(context);
      if (isFoldableSplitEnabled(context)) {
         ruleController.setRules(RuleController.parseRules(context, R.xml.main_split_config));
      } else {
         ruleController.clearRules();
      }

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
}
