package ai.careerpilot.document;

import jakarta.validation.constraints.Pattern;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resumes")
public class ResumeManagementController {
    private final ResumeManagementService service;
    public ResumeManagementController(ResumeManagementService service){this.service=service;}
    @GetMapping public List<ResumeManagementService.AssetResponse> list(Authentication auth){return service.list(auth.getName());}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeManagementService.AssetResponse> upload(Authentication auth,
        @RequestPart("file") MultipartFile file,
        @RequestParam(defaultValue="ORIGINAL") @Pattern(regexp="ORIGINAL|TAILORED") String kind) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(auth.getName(),file,kind));
    }
    @GetMapping("/{id}/download")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> download(Authentication auth,@PathVariable UUID id) {
        ResumeManagementService.DownloadedAsset downloaded=service.download(auth.getName(),id);
        var asset=downloaded.asset();
        String disposition=ContentDisposition.attachment().filename(asset.getOriginalFilename(),java.nio.charset.StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.getMediaType()))
            .contentLength(asset.getSizeBytes()).header(HttpHeaders.CONTENT_DISPOSITION,disposition)
            .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CACHE_CONTROL,"private, no-store")
            .body(new org.springframework.core.io.InputStreamResource(downloaded.stream()));
    }
}
