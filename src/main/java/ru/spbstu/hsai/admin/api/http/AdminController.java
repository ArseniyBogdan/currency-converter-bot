package ru.spbstu.hsai.admin.api.http;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.admin.api.http.dto.ObjectIdDTO;
import ru.spbstu.hsai.admin.entities.ApiKey;
import ru.spbstu.hsai.admin.service.ApiKeyService;
import ru.spbstu.hsai.admin.api.http.dto.AdminDTO;
import ru.spbstu.hsai.admin.api.http.dto.ApiKeyDTO;
import ru.spbstu.hsai.admin.api.http.dto.CreateAdminRequest;
import ru.spbstu.hsai.admin.entities.AdminDBO;
import ru.spbstu.hsai.admin.service.AdminService;
import ru.spbstu.hsai.rates.RatesFetcher;

import javax.validation.Valid;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class AdminController {
//    private final UserService userService;
    private final AdminService adminService;
    private final ApiKeyService apiKeyService;
    private final RatesFetcher ratesFetcher;
//    private final TelegramSDK telegramSDK;

    // Получение списка пользователей
//    @GetMapping
//    public Flux<UserDTO> getAllUsers() {
//        return userService.getAllUsers();
//    }

    // Получение списка пользователей
    @GetMapping("/admin")
    public Flux<AdminDTO> getAllAdmins() {
        return adminService.getAllAdmins().map(this::mapToAdminDTO);
    }

    // Создание админа
    @PostMapping("/admin/create")
    public Mono<AdminDTO> createAdmin(
            @Valid @RequestBody CreateAdminRequest request
    ) {
        return adminService.createAdmin(request.getName(), request.getSurname()).map(this::mapToAdminDTO);
    }

    // Получение списка пользователей
//    @GetMapping("/{userId}")
//    public Flux<UserDTO> getUserByChatId() {
//        return userService.getUserByChatId();
//    }

    // Генерация нового API-ключа
    @PostMapping("/keys/generate")
    public Mono<ApiKeyDTO> generateApiKey(
            @Valid @RequestBody ObjectIdDTO adminId
    ) {
        return apiKeyService.generateKey(adminId.getId()).map(this::mapToApiKeyDTO);
    }

    // Отзыв API-ключа
    @PostMapping("/keys/revoke")
    public Mono<Void> revokeApiKey(
            @Valid @RequestBody ObjectIdDTO adminId
    ) {
        return apiKeyService.revokeKey(adminId.getId());
    }

    @PostMapping("/currency/refresh")
    public Mono<Void> forceCurrencyUpdate() {
        return ratesFetcher.scheduleCurrencyUpdate();
    }

    // Рассылка уведомлений
//    @PostMapping("/notify")
//    public Mono<Void> sendGlobalNotification(@RequestBody String request) {
//        return telegramSDK.broadcastNotification(request);
//    }


    private ApiKeyDTO mapToApiKeyDTO(ApiKey apiKey){
        return new ApiKeyDTO(
                apiKey.getKey(),
                apiKey.getAdminId()
        );
    }

    private AdminDTO mapToAdminDTO(AdminDBO adminDBO){
        return new AdminDTO(
                adminDBO.getId(),
                adminDBO.getAdminName(),
                adminDBO.getAdminSurname(),
                adminDBO.getCreated()
        );
    }

}
