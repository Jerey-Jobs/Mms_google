/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.model;

import java.io.IOException;

import org.w3c.dom.smil.SMILMediaElement;
import org.w3c.dom.smil.SMILRegionElement;
import org.w3c.dom.smil.SMILRegionMediaElement;
import org.w3c.dom.smil.Time;
import org.w3c.dom.smil.TimeList;

import android.content.Context;
import android.util.Log;

import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduPart;

public class MediaModelFactory {
    private static final String TAG = LogTag.TAG;

    public static MediaModel getMediaModel(Context context,
            SMILMediaElement sme, LayoutModel layouts, PduBody pb)
            throws IOException, IllegalArgumentException, MmsException {
        String tag = sme.getTagName();
        String src = sme.getSrc();
        PduPart part = findPart(pb, src);

        if (sme instanceof SMILRegionMediaElement) {
            return getRegionMediaModel(
                    context, tag, src, (SMILRegionMediaElement) sme, layouts, part);
        } else {
            return getGenericMediaModel(
                    context, tag, src, sme, part, null);
        }
    }

    private static PduPart findPart(PduBody pb, String src) {
        PduPart part = null;

        if (src != null) {
            src = unescapeXML(src);
            if (src.startsWith("cid:")) {
                part = pb.getPartByContentId("<" + src.substring("cid:".length()) + ">");
            } else {
                part = pb.getPartByName(src);
                if (part == null) {
                    part = pb.getPartByFileName(src);
                    if (part == null) {
                        part = pb.getPartByContentLocation(src);
                    }
                }
            }
        }

        if (part != null) {
            return part;
        }

        throw new IllegalArgumentException("No part found for the model.");
    }

    private static String unescapeXML(String str) {
        return str.replaceAll("&lt;","<")
            .replaceAll("&gt;", ">")
            .replaceAll("&quot;","\"")
            .replaceAll("&apos;","'")
            .replaceAll("&amp;", "&");
    }

    private static MediaModel getRegionMediaModel(Context context,
            String tag, String src, SMILRegionMediaElement srme,
            LayoutModel layouts, PduPart part) throws IOException, MmsException {
        SMILRegionElement sre = srme.getRegion();
        if (sre != null) {
            RegionModel region = layouts.findRegionById(sre.getId());
            if (region != null) {
                return getGenericMediaModel(context, tag, src, srme, part, region);
            }
        } else {
            String rId = null;

            if (tag.equals(SmilHelper.ELEMENT_TAG_TEXT)) {
                rId = LayoutModel.TEXT_REGION_ID;
            } else {
                rId = LayoutModel.IMAGE_REGION_ID;
            }

            RegionModel region = layouts.findRegionById(rId);
            if (region != null) {
                return getGenericMediaModel(context, tag, src, srme, part, region);
            }
        }

        throw new IllegalArgumentException("Region not found or bad region ID.");
    }

    // When we encounter a content type we can't handle, such as "application/vnd.smaf", instead
    // of throwing an exception and crashing, insert an empty TextModel in its place.
    private static MediaModel createEmptyTextModel(Context context,  RegionModel regionModel)
            throws IOException {
        return new TextModel(context, ContentType.TEXT_PLAIN, null, regionModel);
    }

    private static MediaModel getGenericMediaModel(Context context,
            String tag, String src, SMILMediaElement sme, PduPart part,
            RegionModel regionModel) throws IOException, MmsException {
        byte[] bytes = part.getContentType();
        if (bytes == null) {
            throw new IllegalArgumentException(
                    "Content-Type of the part may not be null.");
        }

        String contentType = new String(bytes);
        MediaModel media = null;
        if (tag.equals(SmilHelper.ELEMENT_TAG_TEXT)) {
            media = new TextModel(context, contentType, src,
                    part.getCharset(), part.getData(), regionModel);
        } else if (tag.equals(SmilHelper.ELEMENT_TAG_IMAGE)) {
            media = new ImageModel(context, contentType, src,
                    part.getDataUri(), regionModel);
        } else if (tag.equals(SmilHelper.ELEMENT_TAG_VIDEO)) {
            media = new VideoModel(context, contentType, src,
                    part.getDataUri(), regionModel);
        } else if (tag.equals(SmilHelper.ELEMENT_TAG_AUDIO)) {
            media = new AudioModel(context, contentType, src,
                    part.getDataUri());
        } else if (tag.equals(SmilHelper.ELEMENT_TAG_REF)) {
            if (ContentType.isTextType(contentType)) {
                media = new TextModel(context, contentType, src,
                        part.getCharset(), part.getData(), regionModel);
            } else if (ContentType.isImageType(contentType)) {
                media = new ImageModel(context, contentType, src,
                        part.getDataUri(), regionModel);
            } else if (ContentType.isVideoType(contentType)) {
                media = new VideoModel(context, contentType, src,
                        part.getDataUri(), regionModel);
            } else if (ContentType.isAudioType(contentType)) {
                media = new AudioModel(context, contentType, src,
                        part.getDataUri());
            } else {
                Log.d(TAG, "[MediaModelFactory] getGenericMediaModel Unsupported Content-Type: "
                        + contentType);
                media = createEmptyTextModel(context, regionModel);
            }
        } else {
            throw new IllegalArgumentException("Unsupported TAG: " + tag);
        }

        // Set 'begin' property.
        int begin = 0;
        TimeList tl = sme.getBegin();
        if ((tl != null) && (tl.getLength() > 0)) {
            // We only support a single begin value.
            Time t = tl.item(0);
            begin = (int) (t.getResolvedOffset() * 1000);
        }
        media.setBegin(begin);

        // Set 'duration' property.
        int duration = (int) (sme.getDur() * 1000);
        if (duration <= 0) {
            tl = sme.getEnd();
            if ((tl != null) && (tl.getLength() > 0)) {
                // We only support a single end value.
                Time t = tl.item(0);
                if (t.getTimeType() != Time.SMIL_TIME_INDEFINITE) {
                    duration = (int) (t.getResolvedOffset() * 1000) - begin;

                    if (duration == 0 &&
                            (media instanceof AudioModel || media instanceof VideoModel)) {
                        duration = MmsConfig.getMinimumSlideElementDuration();
                        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                            Log.d(TAG, "[MediaModelFactory] compute new duration for " + tag +
                                    ", duration=" + duration);
                        }
                    }
                }
            }
        }

        media.setDuration(duration);

        if (!MmsConfig.getSlideDurationEnabled()) {
            /**
             * Because The slide duration is not supported by mmsc,
             * the device has to set fill type as FILL_FREEZE.
             * If not, the media will disappear while rotating the screen
             * in the slide show play view.
             */
            media.setFill(sme.FILL_FREEZE);
        } else {
            // Set 'fill' property.
            media.setFill(sme.getFill());
        }
        return media;
    }
}
