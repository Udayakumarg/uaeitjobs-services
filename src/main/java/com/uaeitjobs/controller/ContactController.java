package com.uaeitjobs.controller;

import com.uaeitjobs.dto.ContactDTO;
import com.uaeitjobs.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contact")
@RequiredArgsConstructor
public class ContactController {

    private final EmailService emailService;

    /**
     * Public endpoint — no authentication required.
     * Forwards the contact form to the configured admin email address.
     * Rate-limited at the public tier (120 req/min per IP).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@Valid @RequestBody ContactDTO.ContactRequest request) {
        emailService.sendContactFormEmail(
                request.name(),
                request.email(),
                request.subject(),
                request.message()
        );
    }
}
