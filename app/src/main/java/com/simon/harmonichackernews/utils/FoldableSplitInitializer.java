package com.simon.harmonichackernews.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;
import androidx.window.embedding.RuleController;

import java.util.Collections;
import java.util.List;

public class FoldableSplitInitializer implements Initializer<RuleController> {
   private static RuleController ruleController;

   @NonNull
   @Override
   public RuleController create(@NonNull Context context) {
      ruleController = RuleController.getInstance(context);
      // MainActivity now owns adaptive list/detail navigation with Navigation 3. Clear any rules
      // retained by an upgraded process so the activity receives the complete foldable window.
      ruleController.clearRules();

      return ruleController;
   }

   @NonNull
   @Override
   public List<Class<? extends Initializer<?>>> dependencies() {
      return Collections.emptyList();
   }

   @Deprecated
   public static boolean isFoldableSplitEnabled(Context context) {
      return false;
   }
}
