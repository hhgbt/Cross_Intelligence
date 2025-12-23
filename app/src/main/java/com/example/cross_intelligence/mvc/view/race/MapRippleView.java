package com.example.cross_intelligence.mvc.view.race;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 地图涟漪动效覆盖层
 * 在屏幕像素坐标上绘制圆圈，不随地图缩放变化
 */
public class MapRippleView extends View {

    private Paint paint;
    private float centerX;
    private float centerY;
    private float currentRadius;
    private int currentAlpha;
    private ValueAnimator animator;

    // 动画参数
    private static final int MAX_RADIUS_PX = 100; // 最大半径（像素）
    private static final int ANIMATION_DURATION = 500; // 动画时长（毫秒）
    private static final int COLOR_GREEN = 0xFF4CAF50; // 品牌绿色

    public MapRippleView(Context context) {
        super(context);
        init();
    }

    public MapRippleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MapRippleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_GREEN);
        setVisibility(GONE);
    }

    /**
     * 显示涟漪动效
     * @param x 屏幕X坐标（像素）
     * @param y 屏幕Y坐标（像素）
     */
    public void showRipple(float x, float y) {
        // 取消之前的动画
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        centerX = x;
        centerY = y;
        currentRadius = 0;
        currentAlpha = 255;
        setVisibility(VISIBLE);

        // 创建动画：从0到1
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ANIMATION_DURATION);
        animator.addUpdateListener(animation -> {
            float progress = (Float) animation.getAnimatedValue();
            
            // 半径从0扩散到最大半径
            currentRadius = progress * MAX_RADIUS_PX;
            
            // 透明度从100%逐渐变为0%
            currentAlpha = (int) (255 * (1 - progress));
            
            // 重绘
            invalidate();
        });
        
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // 动画结束时隐藏
                setVisibility(GONE);
            }
        });
        
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (currentRadius > 0 && currentAlpha > 0) {
            // 设置当前透明度
            paint.setAlpha(currentAlpha);
            
            // 绘制圆圈
            canvas.drawCircle(centerX, centerY, currentRadius, paint);
        }
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        setVisibility(GONE);
    }
}







