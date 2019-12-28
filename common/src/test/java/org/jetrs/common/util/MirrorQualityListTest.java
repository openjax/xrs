/* Copyright (c) 2019 LibJ
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

package org.jetrs.common.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.ListIterator;

import org.junit.Test;
import org.libj.util.MirrorList;
import org.libj.util.Numbers;

public class MirrorQualityListTest {
  @Test
  public void test() {
    final MirrorQualityList<String,Float> list = new MirrorQualityList<>(new ArrayList<>(), new ArrayList<>(), new MirrorList.Mirror<String,Float>() {
      @Override
      public Float valueToReflection(final String value) {
        return Float.valueOf(value);
      }

      @Override
      public String reflectionToValue(final Float reflection) {
        return String.valueOf(reflection);
      }
    }, new MirrorQualityList.Qualifier<String,Float>() {
      @Override
      public long valueToQuality(final String value, final int index) {
        return Numbers.Compound.encode(Float.parseFloat(value), index);
      }

      @Override
      public long reflectionToQuality(final Float reflection, final int index) {
        return Numbers.Compound.encode(reflection, index);
      }
    });

    list.add("0.1");
    assertTrue(list.getMirrorList().contains(0.1f));

    list.getMirrorList().add(0.2f);
    assertTrue(list.contains("0.2"));

    final ListIterator<String> stringIterator = list.listIterator();
    stringIterator.next();
    stringIterator.add("0.3");
    assertTrue(list.getMirrorList().contains(0.3f));

    final ListIterator<Float> floatIterator = list.getMirrorList().listIterator();
    floatIterator.next();
    floatIterator.next();
    floatIterator.add(0.7f);
    assertTrue(list.contains("0.7"));

    assertTrue(list.remove("0.1"));
    assertFalse(list.getMirrorList().contains(0.1f));

    assertTrue(list.getMirrorList().remove(0.2f));
    assertFalse(list.contains("0.2"));

    list.getMirrorList().clear();
    assertEquals(0, list.size());
  }
}