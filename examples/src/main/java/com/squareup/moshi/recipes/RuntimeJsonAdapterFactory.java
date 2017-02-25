package com.squareup.moshi.recipes;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class RuntimeJsonAdapterFactory implements JsonAdapter.Factory {
  private final Map<Class<?>, String> typeToLabel = new LinkedHashMap<>();
  private final Class<?> baseType;
  private final String labelKey;

  public RuntimeJsonAdapterFactory(Class<?> baseType, String labelKey) {
    this.baseType = baseType;
    this.labelKey = labelKey;
  }

  public RuntimeJsonAdapterFactory registerSubtype(Class<?> type, String label) {
    if (!baseType.isAssignableFrom(type)) {
      throw new IllegalArgumentException(type + " must be a " + baseType);
    }
    typeToLabel.put(type, label);
    return this;
  }

  @Override
  public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
    if (!baseType.isAssignableFrom(Types.getRawType(type))) {
      return null;
    }
    int size = typeToLabel.size();
    final Map<String, JsonAdapter<?>> labelToDelegate = new LinkedHashMap<>(size);
    final Map<Class<?>, JsonAdapter<?>> subtypeToDelegate = new LinkedHashMap<>(size);
    for (Map.Entry<Class<?>, String> entry : typeToLabel.entrySet()) {
      Class<?> key = entry.getKey();
      String value = entry.getValue();
      JsonAdapter<?> delegate = moshi.nextAdapter(this, key, annotations);
      labelToDelegate.put(value, delegate);
      subtypeToDelegate.put(key, delegate);
    }
    return new JsonAdapter<Object>() {
      @Override public Object fromJson(JsonReader reader) throws IOException {
        // noinspection unchecked
        Map<String, Object> value = (Map<String, Object>) reader.readJsonValue();
        Object label = value.get(labelKey);
        if (label == null) {
          throw new JsonDataException("Missing label for " + labelKey);
        }
        if (!(label instanceof String)) {
          throw new JsonDataException("Label for "
              + labelKey
              + " must be a string but had a value of "
              + label
              + " of type "
              + label.getClass());
        }
        JsonAdapter<?> delegate = labelToDelegate.get(label);
        if (delegate == null) {
          throw new JsonDataException("Type not registered for label: " + label);
        }
        return delegate.fromJsonValue(value);
      }

      @Override public void toJson(JsonWriter writer, Object value) throws IOException {
        JsonAdapter delegate = subtypeToDelegate.get(value.getClass());
        if (delegate == null) {
          throw new IllegalStateException("Type not registered: " + value.getClass());
        }
        // noinspection unchecked
        delegate.toJson(writer, value);
      }
    };
  }
}
