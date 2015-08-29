/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.doubleclick.openrtb;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.io.CharStreams;
import com.google.openrtb.OpenRtb.ContentCategory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Maps between AdX's ad (product, restricted and sensitive) categories,
 * and OpenRTB's IAB-based {@link ContentCategory}.
 */
public class AdCategoryMapper {
  private static final Logger logger = LoggerFactory.getLogger(AdCategoryMapper.class);
  private static ImmutableSet<ContentCategory>[] dcToOpenrtb;
  private static ImmutableSetMultimap<ContentCategory, Integer> openrtbToDc;
  private static ImmutableList<String> dcDesc;
  private static ImmutableMap<ContentCategory, String> openrtbDesc;

  static {
    Pattern pattern = Pattern.compile("(\\d+)\\|(.*)\\|(\\d+)\\|(.*)");
    ImmutableSetMultimap.Builder<ContentCategory, Integer> data = ImmutableSetMultimap.builder();
    Map<ContentCategory, String> openrtbDesc = new LinkedHashMap<>();
    Map<Integer, String> dcDesc = new LinkedHashMap<>();

    try (InputStream isMetadata = AdCategoryMapper.class.getResourceAsStream(
        "/adx-openrtb/category-mapping-openrtb.txt")) {
      for (String line : CharStreams.readLines(new InputStreamReader(isMetadata))) {
        Matcher matcher = pattern.matcher(line);

        if (matcher.matches()) {
          int openrtbCode = Integer.parseInt(matcher.group(1));
          int dcCode = Integer.parseInt(matcher.group(3));
          ContentCategory openrtbCat = ContentCategory.valueOf(openrtbCode);
          if (openrtbCat == null) {
            logger.warn("Ignoring unknown ContentCategory code: {}", line);
          } else {
            data.put(openrtbCat, dcCode);
            dcDesc.put(dcCode, matcher.group(4));
            openrtbDesc.put(openrtbCat, matcher.group(2));
          }
        }
      }

      openrtbToDc = data.build();
      AdCategoryMapper.openrtbDesc = ImmutableMap.copyOf(openrtbDesc);
      dcToOpenrtb = MapperUtil.multimapIntToSets(openrtbToDc.inverse());
      ImmutableList.Builder<String> dcDescBuilder = ImmutableList.builder();
      for (int i = 0; i < dcToOpenrtb.length; ++i) {
        dcDescBuilder.add(Strings.nullToEmpty(dcDesc.get(i)));
      }
      AdCategoryMapper.dcDesc = dcDescBuilder.build();
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static ImmutableSet<ContentCategory> toOpenRtb(int dc) {
    return MapperUtil.get(dcToOpenrtb, dc);
  }

  public static ImmutableSet<Integer> toDoubleClick(ContentCategory openrtb) {
    ImmutableSet<Integer> dc = openrtbToDc.get(openrtb);
    return dc.contains(0) && dc.size() > 1 ? ImmutableSet.<Integer>of(0) : dc;
  }

  public static EnumSet<ContentCategory> toOpenRtb(
      Collection<Integer> dcList, @Nullable EnumSet<ContentCategory> openrtbSet) {
    EnumSet<ContentCategory> ret = openrtbSet == null
        ? EnumSet.noneOf(ContentCategory.class)
        : openrtbSet;
    for (int dc : dcList) {
      ret.addAll(toOpenRtb(dc));
    }
    return ret;
  }

  public static Set<Integer> toDoubleClick(
      Collection<ContentCategory> openrtbList, @Nullable Set<Integer> dcSet) {
    Set<Integer> ret = dcSet == null ? new LinkedHashSet<Integer>() : dcSet;
    for (ContentCategory openrtb : openrtbList) {
      ret.addAll(toDoubleClick(openrtb));
    }
    return ret;
  }

  static ImmutableMap<ContentCategory, String> openrtbDesc() {
    return openrtbDesc;
  }

  static ImmutableList<String> dcDesc() {
    return dcDesc;
  }
}
