package com.jobportal.recruiter_service.controller;

import com.jobportal.recruiter_service.dto.UserDto;
import com.jobportal.recruiter_service.entity.RecruiterProfile;
import com.jobportal.recruiter_service.feign.client.UserServiceClient;
import com.jobportal.recruiter_service.services.RecruiterProfileService;
import com.jobportal.recruiter_service.util.FileUploadUtil;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/recruiter-profile")
public class RecruiterProfileController {

    private final UserServiceClient userServiceClient;
    private final RecruiterProfileService recruiterProfileService;

    public RecruiterProfileController(UserServiceClient userServiceClient,
                                      RecruiterProfileService recruiterProfileService) {
        this.userServiceClient = userServiceClient;
        this.recruiterProfileService = recruiterProfileService;
    }

    @GetMapping("/")
    public String recruiterProfile(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            UserDto userDto = userServiceClient.getUserByEmail(authentication.getName());
            if (userDto != null) {
                Optional<RecruiterProfile> recruiterProfile = recruiterProfileService.getOne(userDto.userId());
                if (!recruiterProfile.isEmpty())
                    model.addAttribute("profile", recruiterProfile.get());
            }
        }
        if (!model.containsAttribute("profile")) {
            model.addAttribute("profile", new RecruiterProfile());
        }
        return "recruiter_profile";
    }

    @PostMapping("/addNew")
    public String addNew(RecruiterProfile recruiterProfile,
                         @RequestParam(value = "image", required = false) MultipartFile multipartFile,
                         Model model,
                         RedirectAttributes redirectAttributes,
                         HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            UserDto userDto = userServiceClient.getUserByEmail(authentication.getName());
            if (userDto != null) {
                recruiterProfile.setUserAccountId(userDto.userId());
            }
        }

        // Server-side image validation
        if (multipartFile != null && !multipartFile.isEmpty()) {
            String contentType = multipartFile.getContentType();
            String fname = multipartFile.getOriginalFilename() != null ? multipartFile.getOriginalFilename().toLowerCase() : "";
            boolean validType = (contentType != null && (contentType.equalsIgnoreCase("image/jpeg")
                    || contentType.equalsIgnoreCase("image/png")))
                    || fname.endsWith(".jpg") || fname.endsWith(".jpeg") || fname.endsWith(".png");
            long sizeBytes = multipartFile.getSize();
            if (!validType) {
                redirectAttributes.addFlashAttribute("error",
                        "Invalid file type. Only JPEG and PNG images are allowed.");
                return "redirect:/recruiter-profile/";
            }
            if (sizeBytes > 5L * 1024 * 1024) {
                redirectAttributes.addFlashAttribute("error",
                        "Image is too large. Maximum allowed size is 5 MB.");
                return "redirect:/recruiter-profile/";
            }
        }

        model.addAttribute("profile", recruiterProfile);
        String fileName = "";
        if (multipartFile != null && !multipartFile.isEmpty()
                && multipartFile.getOriginalFilename() != null
                && !multipartFile.getOriginalFilename().isEmpty()) {
            fileName = StringUtils.cleanPath(multipartFile.getOriginalFilename());
            recruiterProfile.setProfilePhoto(fileName);
        }
        RecruiterProfile savedUser = recruiterProfileService.addNew(recruiterProfile);
        if (multipartFile != null && !fileName.isEmpty()) {
            String uploadDir = "photos/recruiter/" + savedUser.getUserAccountId();
            try {
                FileUploadUtil.saveFile(uploadDir, fileName, multipartFile);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return buildGatewayRedirectUrl(request, "dashboard/");
    }

    private String buildGatewayRedirectUrl(HttpServletRequest request, String path) {
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String host;
        if (forwardedHost != null && !forwardedHost.isEmpty()) {
            host = forwardedHost.split(",")[0].trim();
            if (host.contains(":")) host = host.substring(0, host.lastIndexOf(":"));
        } else {
            host = "localhost";
        }
        String proto = (forwardedProto != null && !forwardedProto.isEmpty()) ? forwardedProto.split(",")[0].trim() : "http";
        return "redirect:" + proto + "://" + host + ":8080/" + path;
    }
}

