package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Arrays;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WfeFileVariable implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("name")
    private String name;

    @JsonProperty("contentType")
    private String contentType;

    @JsonProperty("data")
    private byte[] data;

    @JsonProperty("stringValue")
    private String stringValue;

    // Конструкторы
    public WfeFileVariable() {}

    public WfeFileVariable(String name, String contentType, byte[] data, String stringValue) {
        this.name = name;
        this.contentType = contentType;
        this.data = data;
        this.stringValue = stringValue;
    }

    public WfeFileVariable(String name, String contentType, byte[] data) {
        this(name, contentType, data, name);
    }

    // Геттеры и сеттеры
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }

    public String getStringValue() { return stringValue; }
    public void setStringValue(String stringValue) { this.stringValue = stringValue; }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WfeFileVariable that = (WfeFileVariable) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(contentType, that.contentType) &&
                Objects.equals(stringValue, that.stringValue) &&
                Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, contentType, stringValue);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return String.format("WfeFileVariable{name='%s', contentType='%s', data.length=%d, stringValue='%s'}",
                name, contentType, data != null ? data.length : 0, stringValue);
    }

}