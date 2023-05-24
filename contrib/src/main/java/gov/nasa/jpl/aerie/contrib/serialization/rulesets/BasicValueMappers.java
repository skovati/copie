package gov.nasa.jpl.aerie.contrib.serialization.rulesets;

import gov.nasa.jpl.aerie.contrib.serialization.mappers.ArrayValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.BooleanValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.ByteValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.CharacterValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.DoubleValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.DurationValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.EnumValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.FloatValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.IntegerValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.ListValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.LongValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.MapValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.PathValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.PrimitiveBooleanArrayValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.PrimitiveByteArrayValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.PrimitiveCharArrayValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.PrimitiveDoubleArrayValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.PrimitiveFloatArrayValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.PrimitiveIntArrayValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.PrimitiveLongArrayValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.PrimitiveShortArrayValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.ShortValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.StringValueMapper;
import gov.nasa.jpl.aerie.contrib.serialization.mappers.UnitValueMapper;
import gov.nasa.jpl.aerie.merlin.framework.ValueMapper;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.Unit;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class BasicValueMappers {

  // Unit is an enum, so `$unit` needs to be defined before `$enum()`
  // in order to override the latter's representation.
  public static ValueMapper<Unit> $unit() { return new UnitValueMapper(); }

  public static ValueMapper<Boolean> $boolean() {
    return new BooleanValueMapper();
  }

  public static ValueMapper<Byte> $byte() {
    return new ByteValueMapper();
  }

  public static ValueMapper<Short> $short() {
    return new ShortValueMapper();
  }

  public static ValueMapper<Integer> $int() {
    return new IntegerValueMapper();
  }

  public static ValueMapper<Long> $long() {
    return new LongValueMapper();
  }

  public static ValueMapper<Character> $char() {
    return new CharacterValueMapper();
  }

  public static ValueMapper<Float> $float() {
    return new FloatValueMapper();
  }

  public static ValueMapper<Double> $double() {
    return new DoubleValueMapper();
  }


  public static ValueMapper<byte[]> byteArray() {
    return new PrimitiveByteArrayValueMapper();
  }

  public static ValueMapper<short[]> shortArray() {
    return new PrimitiveShortArrayValueMapper();
  }

  public static ValueMapper<int[]> intArray() {
    return new PrimitiveIntArrayValueMapper();
  }

  public static ValueMapper<long[]> longArray() {
    return new PrimitiveLongArrayValueMapper();
  }

  public static ValueMapper<float[]> floatArray() {
    return new PrimitiveFloatArrayValueMapper();
  }

  public static ValueMapper<double[]> doubleArray() {
    return new PrimitiveDoubleArrayValueMapper();
  }

  public static ValueMapper<char[]> charArray() {
    return new PrimitiveCharArrayValueMapper();
  }

  public static ValueMapper<boolean[]> booleanArray() {
    return new PrimitiveBooleanArrayValueMapper();
  }


  public static <T> ValueMapper<T[]> array(final Class<T> elementClass, final ValueMapper<T> elementMapper) {
    return new ArrayValueMapper<>(elementMapper, elementClass);
  }

  public static <E extends Enum<E>> ValueMapper<E> $enum(final Class<E> enumClass) {
    return new EnumValueMapper<>(enumClass);
  }


  public static ValueMapper<String> string() {
    return new StringValueMapper();
  }

  public static <T> ValueMapper<List<T>> list(final ValueMapper<T> elementMapper) {
    return new ListValueMapper<>(elementMapper);
  }

  public static <K, V> ValueMapper<Map<K, V>> map(final ValueMapper<K> keyMapper, final ValueMapper<V> valueMapper) {
    return new MapValueMapper<>(keyMapper, valueMapper);
  }

  public static ValueMapper<Duration> duration() {
    return new DurationValueMapper();
  }

  public static ValueMapper<Path> path() {
    return new PathValueMapper();
  }
}
