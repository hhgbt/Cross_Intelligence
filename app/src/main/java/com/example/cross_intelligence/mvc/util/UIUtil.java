package com.example.cross_intelligence.mvc.util;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * UI 辅助工具，封装 Toast 与 Dialog 的线程安全调用。
 */
public final class UIUtil {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final AtomicReference<Toast> LAST_TOAST = new AtomicReference<>();

    private UIUtil() {
    }

    public static void showToast(@NonNull Context context, @NonNull CharSequence text) {
        Runnable showTask = () -> {
            Toast toast = Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT);
            Toast previous = LAST_TOAST.getAndSet(toast);
            if (previous != null) {
                previous.cancel();
            }
            toast.show();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showTask.run();
        } else {
            MAIN_HANDLER.post(showTask);
        }
    }

    public static DialogBuilder dialog(@NonNull Context context) {
        return new DialogBuilder(context);
    }

    public static final class DialogBuilder {
        private final Context context;
        private CharSequence title;
        private CharSequence message;
        private CharSequence positiveText;
        private CharSequence negativeText;
        private Runnable positiveAction;
        private Runnable negativeAction;

        private DialogBuilder(Context context) {
            this.context = context;
        }

        public DialogBuilder title(@Nullable CharSequence title) {
            this.title = title;
            return this;
        }

        public DialogBuilder message(@Nullable CharSequence message) {
            this.message = message;
            return this;
        }

        public DialogBuilder positive(@NonNull CharSequence text, @Nullable Runnable action) {
            this.positiveText = text;
            this.positiveAction = action;
            return this;
        }

        public DialogBuilder negative(@NonNull CharSequence text, @Nullable Runnable action) {
            this.negativeText = text;
            this.negativeAction = action;
            return this;
        }

        public AlertDialog show() {
            AlertDialog.Builder builder = new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message);
            if (positiveText != null) {
                builder.setPositiveButton(positiveText, (dialog, which) -> {
                    if (positiveAction != null) {
                        positiveAction.run();
                    }
                });
            }
            if (negativeText != null) {
                builder.setNegativeButton(negativeText, (dialog, which) -> {
                    if (negativeAction != null) {
                        negativeAction.run();
                    }
                });
            }
            AlertDialog dialog = builder.create();
            dialog.show();
            return dialog;
        }
    }
}





