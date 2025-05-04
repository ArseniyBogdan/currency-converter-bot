package ru.spbstu.hsai.admin.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bson.types.ObjectId;

@Data
@AllArgsConstructor
public class ApiKey {
    private ObjectId adminId;
    private String key;
}
