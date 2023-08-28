package com.simon.harmonichackernews.utils;

import android.content.Context;

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
      if (!isSplitSupported(context)) {
         return ruleController;
      }

      setFoldableSupport(context, SettingsUtils.shouldEnableFoldableSupport(context));
      return ruleController;
   }

   @NonNull
   @Override
   public List<Class<? extends Initializer<?>>> dependencies() {
      return Collections.emptyList();
   }

   private void setFoldableSupport(Context context, boolean enabled) {
      if (enabled) {
         ruleController.setRules(RuleController.parseRules(context, R.xml.main_split_config));
      } else {
         ruleController.clearRules();
      }
   }

   public static boolean isSplitSupported(Context context) {
      return SplitController.getInstance(context).getSplitSupportStatus().equals(SplitSupportStatus.SPLIT_AVAILABLE);
   }
}
