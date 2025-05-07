package ru.spbstu.hsai.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.admin.dao.AdminDAO;
import ru.spbstu.hsai.admin.entities.AdminDBO;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {
    private final AdminDAO adminDAO;

    public Flux<AdminDBO> getAllAdmins(){
        log.info("Executing getAllAdmins");
        return adminDAO.findAll();
    }

    public Mono<AdminDBO> createAdmin(String adminName, String adminSurname){
        log.info("Executing generateApiKeyForAdmin with adminName: {} and adminSurname: {}", adminName, adminSurname);
        return adminDAO.save(new AdminDBO(
                null,
                adminName,
                adminSurname,
                LocalDateTime.now()
        ));
    }

    public Mono<AdminDBO> getAdminById(ObjectId adminId){
        log.info("Executing getAdminById: {}", adminId);
        return adminDAO.findById(adminId);
    }
}
