/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.cyngn.eleven.R;
import com.cyngn.eleven.cache.ImageFetcher;
import com.cyngn.eleven.cache.ImageWorker;

public class BlurScrimImage extends FrameLayout {
    private ImageView mImageView;

    public BlurScrimImage(Context context, AttributeSet attrs) {
        super(context, attrs);
    }



    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mImageView = (ImageView)findViewById(R.id.blurImage);
    }

    public ImageView getImageView() {
        return mImageView;
    }

    /**
     * Transitions the image to the default state (default blur artwork)
     */
    public void transitionToDefaultState() {
        Bitmap blurredBitmap = ((BitmapDrawable) getResources().getDrawable(R.drawable.default_artwork_blur)).getBitmap();

        TransitionDrawable imageTransition = ImageWorker.createImageTransitionDrawable(getResources(),
                mImageView.getDrawable(), blurredBitmap, ImageWorker.FADE_IN_TIME_SLOW, true, true);

        TransitionDrawable paletteTransition = ImageWorker.createPaletteTransition(this,
                Color.TRANSPARENT);


        setTransitionDrawable(imageTransition, paletteTransition);
    }

    /**
     * Sets the transition drawable
     * @param imageTransition the transition for the imageview
     * @param paletteTransition the transition for the scrim overlay
     */
    public void setTransitionDrawable(TransitionDrawable imageTransition,
                               TransitionDrawable paletteTransition) {
        setBackground(paletteTransition);
        mImageView.setImageDrawable(imageTransition);
    }

    /**
     * Loads the current artwork into this BlurScrimImage
     * @param imageFetcher an ImageFetcher instance
     */
    public void loadBlurImage(ImageFetcher imageFetcher) {
        imageFetcher.loadCurrentBlurredArtwork(this);
    }
}