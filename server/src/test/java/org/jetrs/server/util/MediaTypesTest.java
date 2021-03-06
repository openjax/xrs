/* Copyright (c) 2016 JetRS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.jetrs.server.util;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.ws.rs.core.MediaType;

import org.jetrs.common.util.MediaTypes;
import org.junit.Assert;
import org.junit.Test;

public class MediaTypesTest {
  private static void same(final MediaType specific, final MediaType general) {
    assertSame(specific, MediaTypes.getCompatible(specific, general));
    assertSame(specific, MediaTypes.getCompatible(general, specific));
    assertSame(specific, MediaTypes.getCompatible(specific, specific));
  }

  private static void equals(final MediaType specific, final MediaType general) {
    assertEquals(specific, MediaTypes.getCompatible(specific, general));
    assertEquals(specific, MediaTypes.getCompatible(general, specific));
    assertEquals(specific, MediaTypes.getCompatible(specific, specific));
  }

  private static void testParse(final Consumer<MediaType[]> c, final String ... headers) {
    c.accept(MediaTypes.parse(headers));
    c.accept(MediaTypes.parse(headers == null ? null : Arrays.asList(headers)));
    c.accept(MediaTypes.parse(headers == null ? null : Collections.enumeration(Arrays.asList(headers))));
  }

  @Test
  public void testError() {
    testParse(Assert::assertNull, (String[])null);
    testParse(m -> assertArrayEquals(new MediaType[0], m), (String)null);
    testParse(m -> assertArrayEquals(new MediaType[0], m), "");
    testParse(m -> assertEquals(3, m.length), "application/json; q=\"oops\" ; charset=\"utf8\";  ", "application/xml; q= ; charset=\"utf8\";  ", "application/json; q=\"oops\" ; charset=\"utf8\";  ");
    testParse(m -> assertEquals(3, m.length), "application/json; q=\"oops\" ; charset;  ", "application/xml; q= ; charset=\"utf8\";  ", "application/json; q=\"oops\" ; charset=\"utf8\";  ");
    testParse(m -> assertEquals(3, m.length), "application/json; q=\"oops\" ; charset;  ", "application/xml; q= ; charset=\"utf8\";  ", "application/json; ;;;");
    testParse(m -> assertEquals(3, m.length), "application/json; q=\"oops\" ; charset;  , application/xml; q= ; charset=\"utf8\";  ", "application/json; ;;;");
    testParse(m -> assertEquals(3, m.length), "application/json; q=\"oops\" ; charset;  , application/xml; q= ; charset=\"utf8\";  ,application/json; ;;;");
  }

  @Test
  public void testParse() {
    assertEquals(new MediaType("application", "json"), MediaType.valueOf("application/json"));
    assertEquals(new MediaType("application", "json", "utf8"), MediaType.valueOf("application/json; charset=utf8"));
    assertEquals(new MediaType("application", "json", Collections.singletonMap("charset", "utf8")), MediaType.valueOf("application/json;charset=utf8 "));
    final Map<String,String> parameters = new HashMap<>();
    parameters.put("charset", "utf8");
    parameters.put("q", ".5");
    assertEquals(new MediaType("application", "json", parameters), MediaType.valueOf("application/json;charset=utf8; q=.5"));
    assertEquals(new MediaType("application", "json", parameters), MediaType.valueOf("application/json;charset=\"utf8\"; q=.5"));
    parameters.put("q", "oops");
    assertEquals(new MediaType("application", "json", parameters), MediaType.valueOf("application/json; q=\"oops\" ; charset=\"utf8\";  "));
  }

  @Test
  public void testCompatible() {
    same(null, null);

    MediaType mediaType1 = new MediaType();
    same(mediaType1, null);

    mediaType1 = MediaType.valueOf("application/json");
    same(mediaType1, new MediaType());

    mediaType1 = MediaType.valueOf("application/json");
    MediaType mediaType2 = MediaType.valueOf("application/*");
    same(mediaType1, mediaType2);

    mediaType2 = MediaType.valueOf("application/xml");
    assertNull(MediaTypes.getCompatible(mediaType1, mediaType2));

    mediaType2 = MediaType.valueOf("application/xml+json");
    same(mediaType1, mediaType2);


    // NOTE: It is not clear whether the first match for a subtype with a suffix should be
    // NOTE: for the prefix+suffix, or the prefix?
//    mediaType2 = MediaType.valueOf("application/json+xml");
//    same(mediaType1, mediaType2);

    mediaType1 = MediaType.valueOf("application/json;charset=utf-8");
    equals(mediaType1, mediaType2);
//
    mediaType1 = MediaType.valueOf("application/json;charset=utf-8;q=.5");
    assertEquals(MediaType.valueOf("application/json;charset=utf-8"), MediaTypes.getCompatible(mediaType1, mediaType2));

    mediaType2 = MediaType.valueOf("application/json;charset=utf-8;q=.8");
    assertEquals(MediaType.valueOf("application/json;charset=utf-8"), MediaTypes.getCompatible(mediaType1, mediaType2));

    mediaType1 = MediaType.valueOf("application/json;charset=utf-8");
    assertEquals(MediaType.valueOf("application/json;charset=utf-8"), MediaTypes.getCompatible(mediaType1, mediaType2));

    mediaType2 = MediaType.valueOf("application/json;charset=utf-8;x=3;q=.8");
    assertEquals(MediaType.valueOf("application/json;charset=utf-8;x=3"), MediaTypes.getCompatible(mediaType1, mediaType2));

    mediaType1 = MediaType.valueOf("application/json;charset=utf-8;y=foo");
    assertEquals(MediaType.valueOf("application/json;charset=utf-8;x=3;y=foo"), MediaTypes.getCompatible(mediaType1, mediaType2));

    mediaType2 = MediaType.valueOf("application/foo+json;charset=utf-8;x=3;q=.8");
    assertEquals(MediaType.valueOf("application/json;charset=utf-8;x=3;y=foo"), MediaTypes.getCompatible(mediaType1, mediaType2));

    mediaType1 = MediaType.valueOf("application/bar+json;charset=utf-8;y=foo");
    assertNull(MediaTypes.getCompatible(mediaType1, mediaType2));

    mediaType2 = MediaType.valueOf("application/*+json;charset=utf-8;x=3;q=.8");
    assertEquals(MediaType.valueOf("application/json;charset=utf-8;x=3;y=foo"), MediaTypes.getCompatible(mediaType1, mediaType2));
  }
}