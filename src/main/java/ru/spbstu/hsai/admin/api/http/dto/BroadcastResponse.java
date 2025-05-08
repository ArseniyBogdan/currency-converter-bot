package ru.spbstu.hsai.admin.api.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BroadcastResponse {
    @JsonProperty("sentCount") private Integer sentCount;
    @JsonProperty("failedCount") private Integer failedCount;
}
