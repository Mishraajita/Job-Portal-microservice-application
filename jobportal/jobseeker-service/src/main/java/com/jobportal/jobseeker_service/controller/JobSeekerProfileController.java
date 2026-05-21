package com.jobportal.jobseeker_service.controller;

import com.jobportal.jobseeker_service.dto.UserDto;
import com.jobportal.jobseeker_service.entity.JobSeekerProfile;
import com.jobportal.jobseeker_service.entity.Skills;
import com.jobportal.jobseeker_service.feign.client.UserServiceClient;
import com.jobportal.jobseeker_service.services.JobSeekerProfileService;
import com.jobportal.jobseeker_service.util.FileDownloadUtil;
import com.jobportal.jobseeker_service.util.FileUploadUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/job-seeker-profile")
public class JobSeekerProfileController {

    private final JobSeekerProfileService jobSeekerProfileService;
    private final UserServiceClient userServiceClient;

    @Autowired
    public JobSeekerProfileController(JobSeekerProfileService jobSeekerProfileService,
                                      UserServiceClient userServiceClient) {
        this.jobSeekerProfileService = jobSeekerProfileService;
        this.userServiceClient = userServiceClient;
    }

    @GetMapping("/")
    public String jobSeekerProfile(Model model) {
        JobSeekerProfile jobSeekerProfile = new JobSeekerProfile();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<Skills> skills = new ArrayList<>();

        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            UserDto userDto = userServiceClient.getUserByEmail(authentication.getName());
            if (userDto != null) {
                Optional<JobSeekerProfile> seekerProfile = jobSeekerProfileService.getOne(userDto.userId());
                if (seekerProfile.isPresent()) {
                    jobSeekerProfile = seekerProfile.get();
                    if (jobSeekerProfile.getSkills().isEmpty()) {
                        skills.add(new Skills());
                        jobSeekerProfile.setSkills(skills);
                    }
                }
            }
        }

        model.addAttribute("skills", skills);
        model.addAttribute("profile", jobSeekerProfile);
        return "job-seeker-profile";
    }

    @PostMapping("/addNew")
    public String addNew(JobSeekerProfile jobSeekerProfile,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         @RequestParam(value = "pdf", required = false) MultipartFile pdf,
                         Model model,
                         RedirectAttributes redirectAttributes,
                         HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            UserDto userDto = userServiceClient.getUserByEmail(authentication.getName());
            if (userDto != null) {
                jobSeekerProfile.setUserAccountId(userDto.userId());
            }
        }
        
        // Validate profile photo type
        if (image != null && !image.isEmpty()) {
            String ct = image.getContentType();
            String fname = image.getOriginalFilename() != null ? image.getOriginalFilename().toLowerCase() : "";
            boolean validType = (ct != null && (ct.equalsIgnoreCase("image/jpeg") || ct.equalsIgnoreCase("image/png")))
                    || fname.endsWith(".jpg") || fname.endsWith(".jpeg") || fname.endsWith(".png");
            if (!validType) {
                redirectAttributes.addFlashAttribute("error", "Profile photo must be a JPEG or PNG image.");
                return buildGatewayRedirectUrl(request, "job-seeker-profile/");
            }
        }
        
        // Validate resume type
        if (pdf != null && !pdf.isEmpty()) {
            String ct = pdf.getContentType();
            String fname = pdf.getOriginalFilename() != null ? pdf.getOriginalFilename().toLowerCase() : "";
            boolean validType = (ct != null && (ct.equalsIgnoreCase("application/pdf")
                    || ct.equalsIgnoreCase("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                    || fname.endsWith(".pdf") || fname.endsWith(".docx");
            if (!validType) {
                redirectAttributes.addFlashAttribute("error", "Resume must be a PDF or DOCX file.");
                return buildGatewayRedirectUrl(request, "job-seeker-profile/");
            }
        }


        model.addAttribute("profile", jobSeekerProfile);
        model.addAttribute("skills", new ArrayList<>());

        for (Skills skill : jobSeekerProfile.getSkills()) {
            skill.setJobSeekerProfile(jobSeekerProfile);
        }

        String imageName = "";
        String resumeName = "";

        if (image != null && !Objects.equals(image.getOriginalFilename(), "")) {
            imageName = StringUtils.cleanPath(Objects.requireNonNull(image.getOriginalFilename()));
            jobSeekerProfile.setProfilePhoto(imageName);
        }
        if (pdf != null && !Objects.equals(pdf.getOriginalFilename(), "")) {
            resumeName = StringUtils.cleanPath(Objects.requireNonNull(pdf.getOriginalFilename()));
            jobSeekerProfile.setResume(resumeName);
        }

        jobSeekerProfileService.addNew(jobSeekerProfile);

        try {
            String uploadDir = "photos/candidate/" + jobSeekerProfile.getUserAccountId();
            if (image != null && !Objects.equals(image.getOriginalFilename(), ""))
                FileUploadUtil.saveFile(uploadDir, imageName, image);
            if (pdf != null && !Objects.equals(pdf.getOriginalFilename(), ""))
                FileUploadUtil.saveFile(uploadDir, resumeName, pdf);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return buildGatewayRedirectUrl(request, "dashboard/");
    }

    @GetMapping("/{id}")
    public String candidateProfile(@PathVariable("id") int id,
                                   @RequestParam(value = "coverLetter", required = false) String coverLetter,
                                   Model model) {
        Optional<JobSeekerProfile> seekerProfile = jobSeekerProfileService.getOne(id);
        seekerProfile.ifPresent(p -> model.addAttribute("profile", p));
        if (coverLetter != null && !coverLetter.isEmpty()) {
            model.addAttribute("coverLetter", coverLetter);
        }
        return "job-seeker-profile";
    }


    @GetMapping("/downloadResume")
    public ResponseEntity<?> downloadResume(@RequestParam(value = "fileName") String fileName,
                                            @RequestParam(value = "userID") String userId) {
        FileDownloadUtil downloadUtil = new FileDownloadUtil();
        Resource resource;
        try {
            resource = downloadUtil.getFileAsResource("photos/candidate/" + userId, fileName);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
        if (resource == null) {
            return new ResponseEntity<>("File not found", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
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