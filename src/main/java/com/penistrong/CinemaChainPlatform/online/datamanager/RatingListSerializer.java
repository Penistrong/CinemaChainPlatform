package com.penistrong.CinemaChainPlatform.online.datamanager;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.penistrong.CinemaChainPlatform.online.model.Rating;

import java.io.IOException;
import java.util.List;

//用于转换为Json对象时，自定义序列化评分列表
public class RatingListSerializer extends JsonSerializer<List<Rating>> {

    @Override
    public void serialize(List<Rating> ratingList, JsonGenerator jsonGenerator,
                          SerializerProvider provider) throws IOException {
        jsonGenerator.writeStartArray();
        for (Rating rating : ratingList) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeObjectField("rating", rating);
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndArray();
    }
}
