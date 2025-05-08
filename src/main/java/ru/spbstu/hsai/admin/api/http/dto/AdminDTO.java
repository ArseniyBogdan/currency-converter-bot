package ru.spbstu.hsai.admin.api.http.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonProperty("adminId") private ObjectId adminId;
    @JsonProperty("adminName") private String adminName;
    @JsonProperty("adminSurname") private String adminSurname;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("created") private LocalDateTime created;
}