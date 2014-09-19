/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.widget.ImageView;

import com.cyngn.eleven.cache.BitmapWorkerTask;
import com.cyngn.eleven.cache.ImageCache;
import com.cyngn.eleven.cache.ImageWorker;
import com.cyngn.eleven.lastfm.Album;
import com.cyngn.eleven.lastfm.Artist;
import com.cyngn.eleven.lastfm.ImageSize;
import com.cyngn.eleven.lastfm.MusicEntry;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ImageUtils {
    private static final String DEFAULT_HTTP_CACHE_DIR = "http"; //$NON-NLS-1$

    public static final int IO_BUFFER_SIZE_BYTES = 1024;

    private static final int DEFAULT_MAX_IMAGE_HEIGHT = 1024;

    private static final int DEFAULT_MAX_IMAGE_WIDTH = 1024;

    /**
     * Gets the image url based on the imageType
     * @param artistName The artist name param used in the Last.fm API.
     * @param albumName The album name param used in the Last.fm API.
     * @param imageType The type of image URL to fetch for.
     * @return The image URL for an artist image or album image.
     */
    public static String processImageUrl(final Context context, final String artistName,
                                         final String albumName, final ImageWorker.ImageType imageType) {
        switch (imageType) {
            case ARTIST:
                if (!TextUtils.isEmpty(artistName)) {
                    if (PreferenceUtils.getInstance(context).downloadMissingArtistImages()) {
                        final Artist artist = Artist.getInfo(context, artistName);
                        if (artist != null) {
                            return getBestImage(artist);
                        }
                    }
                }
                break;
            case ALBUM:
                if (!TextUtils.isEmpty(artistName) && !TextUtils.isEmpty(albumName)) {
                    if (PreferenceUtils.getInstance(context).downloadMissingArtwork()) {
                        final Artist correction = Artist.getCorrection(context, artistName);
                        if (correction != null) {
                            final Album album = Album.getInfo(context, correction.getName(),
                                    albumName);
                            if (album != null) {
                                return getBestImage(album);
                            }
                        }
                    }
                }
                break;
            default:
                break;
        }
        return null;
    }

    /**
     * Downloads the bitmap from the url and returns it after some processing
     *
     * @param key The key to identify which image to process, as provided by
     *            {@link ImageWorker#loadImage(mKey, android.widget.ImageView)}
     * @return The processed {@link Bitmap}.
     */
    public static Bitmap processBitmap(final Context context, final String url) {
        if (url == null) {
            return null;
        }
        final File file = downloadBitmapToFile(context, url, DEFAULT_HTTP_CACHE_DIR);
        if (file != null) {
            // Return a sampled down version
            final Bitmap bitmap = decodeSampledBitmapFromFile(file.toString());
            file.delete();
            if (bitmap != null) {
                return bitmap;
            }
        }
        return null;
    }

    /**
     * Decode and sample down a {@link Bitmap} from a file to the requested
     * width and height.
     *
     * @param filename The full path of the file to decode
     * @param reqWidth The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return A {@link Bitmap} sampled down from the original with the same
     *         aspect ratio and dimensions that are equal to or greater than the
     *         requested width and height
     */
    public static Bitmap decodeSampledBitmapFromFile(final String filename) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filename, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, DEFAULT_MAX_IMAGE_WIDTH,
                DEFAULT_MAX_IMAGE_HEIGHT);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filename, options);
    }

    /**
     * Calculate an inSampleSize for use in a
     * {@link android.graphics.BitmapFactory.Options} object when decoding
     * bitmaps using the decode* methods from {@link BitmapFactory}. This
     * implementation calculates the closest inSampleSize that will result in
     * the final decoded bitmap having a width and height equal to or larger
     * than the requested width and height. This implementation does not ensure
     * a power of 2 is returned for inSampleSize which can be faster when
     * decoding but results in a larger bitmap which isn't as useful for caching
     * purposes.
     *
     * @param options An options object with out* params already populated (run
     *            through a decode* method with inJustDecodeBounds==true
     * @param reqWidth The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return The value to be used for inSampleSize
     */
    public static final int calculateInSampleSize(final BitmapFactory.Options options,
                                                  final int reqWidth, final int reqHeight) {
        /* Raw height and width of image */
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = Math.round((float)height / (float)reqHeight);
            } else {
                inSampleSize = Math.round((float)width / (float)reqWidth);
            }

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger
            // inSampleSize).

            final float totalPixels = width * height;

            /* More than 2x the requested pixels we'll sample down further */
            final float totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++;
            }
        }
        return inSampleSize;
    }

    private static String getBestImage(MusicEntry e) {
        final ImageSize[] QUALITY = {ImageSize.EXTRALARGE, ImageSize.LARGE, ImageSize.MEDIUM,
                ImageSize.SMALL, ImageSize.UNKNOWN};
        for(ImageSize q : QUALITY) {
            String url = e.getImageURL(q);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    /**
     * Download a {@link Bitmap} from a URL, write it to a disk and return the
     * File pointer. This implementation uses a simple disk cache.
     *
     * @param context The context to use
     * @param urlString The URL to fetch
     * @return A {@link File} pointing to the fetched bitmap
     */
    public static final File downloadBitmapToFile(final Context context, final String urlString,
                                                  final String uniqueName) {
        final File cacheDir = ImageCache.getDiskCacheDir(context, uniqueName);

        if (!cacheDir.exists()) {
            cacheDir.mkdir();
        }

        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;

        try {
            final File tempFile = File.createTempFile("bitmap", null, cacheDir); //$NON-NLS-1$

            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection)url.openConnection();
            if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            final InputStream in = new BufferedInputStream(urlConnection.getInputStream(),
                    IO_BUFFER_SIZE_BYTES);
            out = new BufferedOutputStream(new FileOutputStream(tempFile), IO_BUFFER_SIZE_BYTES);

            int oneByte;
            while ((oneByte = in.read()) != -1) {
                out.write(oneByte);
            }
            return tempFile;
        } catch (final IOException ignored) {
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (out != null) {
                try {
                    out.close();
                } catch (final IOException ignored) {
                }
            }
        }
        return null;
    }
}
