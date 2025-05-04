package ru.spbstu.hsai.auth.entities;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "api_keys")
public class ApiKeyDBO {
    @Id
    private ObjectId id;
    private Long adminId;
    private String key;
    private Boolean revoked;
}
