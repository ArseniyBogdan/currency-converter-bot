package ru.spbstu.hsai.admin.api.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bson.types.ObjectId;

@Data
@AllArgsConstructor
public class ApiKeyDTO {
    @JsonProperty("apiKey") String apiKey;
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonProperty("adminId") ObjectId adminId;
}
