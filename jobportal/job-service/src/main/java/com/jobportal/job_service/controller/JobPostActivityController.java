package com.jobportal.job_service.controller;

import com.jobportal.job_service.dto.JobSeekerProfileDto;
import com.jobportal.job_service.dto.RecruiterProfileDto;
import com.jobportal.job_service.dto.UserDto;
import com.jobportal.job_service.entity.JobPostActivity;
import com.jobportal.job_service.entity.RecruiterJobsDto;
import com.jobportal.job_service.feign.client.ApplicationServiceClient;
import com.jobportal.job_service.feign.client.JobSeekerServiceClient;
import com.jobportal.job_service.feign.client.RecruiterServiceClient;
import com.jobportal.job_service.feign.client.SavedJobsServiceClient;
import com.jobportal.job_service.feign.client.UserServiceClient;
import com.jobportal.job_service.services.JobPostActivityService;
import com.jobportal.job_service.util.FileUploadUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class JobPostActivityController {

    private final JobPostActivityService jobPostActivityService;
    private final UserServiceClient userServiceClient;
    private final ApplicationServiceClient applicationServiceClient;
    private final SavedJobsServiceClient savedJobsServiceClient;
    private final RecruiterServiceClient recruiterServiceClient;
    private final JobSeekerServiceClient jobSeekerServiceClient;

    @Autowired
    public JobPostActivityController(JobPostActivityService jobPostActivityService,
                                     UserServiceClient userServiceClient,
                                     ApplicationServiceClient applicationServiceClient,
                                     SavedJobsServiceClient savedJobsServiceClient,
                                     RecruiterServiceClient recruiterServiceClient,
                                     JobSeekerServiceClient jobSeekerServiceClient) {
        this.jobPostActivityService = jobPostActivityService;
        this.userServiceClient = userServiceClient;
        this.applicationServiceClient = applicationServiceClient;
        this.savedJobsServiceClient = savedJobsServiceClient;
        this.recruiterServiceClient = recruiterServiceClient;
        this.jobSeekerServiceClient = jobSeekerServiceClient;
    }

    @GetMapping({"/dashboard", "/dashboard/"})
    public String searchJobs(Model model,
                             @RequestParam(value = "job", required = false) String job,
                             @RequestParam(value = "location", required = false) String location,
                             @RequestParam(value = "partTime", required = false) String partTime,
                             @RequestParam(value = "fullTime", required = false) String fullTime,
                             @RequestParam(value = "freelance", required = false) String freelance,
                             @RequestParam(value = "remoteOnly", required = false) String remoteOnly,
                             @RequestParam(value = "officeOnly", required = false) String officeOnly,
                             @RequestParam(value = "partialRemote", required = false) String partialRemote,
                             @RequestParam(value = "today", required = false) boolean today,
                             @RequestParam(value = "days7", required = false) boolean days7,
                             @RequestParam(value = "days30", required = false) boolean days30) {

        model.addAttribute("partTime", Objects.equals(partTime, "Part-Time"));
        model.addAttribute("fullTime", Objects.equals(fullTime, "Full-Time"));
        model.addAttribute("freelance", Objects.equals(freelance, "Freelance"));
        model.addAttribute("remoteOnly", Objects.equals(remoteOnly, "Remote-Only"));
        model.addAttribute("officeOnly", Objects.equals(officeOnly, "Office-Only"));
        model.addAttribute("partialRemote", Objects.equals(partialRemote, "Partial-Remote"));
        model.addAttribute("today", today);
        model.addAttribute("days7", days7);
        model.addAttribute("days30", days30);
        model.addAttribute("job", job);
        model.addAttribute("location", location);

        LocalDate searchDate = null;
        List<JobPostActivity> jobPost;
        boolean dateSearchFlag = true;
        boolean remote = true;
        boolean type = true;

        if (days30) searchDate = LocalDate.now().minusDays(30);
        else if (days7) searchDate = LocalDate.now().minusDays(7);
        else if (today) searchDate = LocalDate.now();
        else dateSearchFlag = false;

        if (partTime == null && fullTime == null && freelance == null) {
            partTime = "Part-Time"; fullTime = "Full-Time"; freelance = "Freelance"; remote = false;
        }
        if (officeOnly == null && remoteOnly == null && partialRemote == null) {
            officeOnly = "Office-Only"; remoteOnly = "Remote-Only"; partialRemote = "Partial-Remote"; type = false;
        }

        if (!dateSearchFlag && !remote && !type && !StringUtils.hasText(job) && !StringUtils.hasText(location)) {
            jobPost = jobPostActivityService.getAll();
        } else {
            jobPost = jobPostActivityService.search(job, location,
                    Arrays.asList(partTime, fullTime, freelance),
                    Arrays.asList(remoteOnly, officeOnly, partialRemote), searchDate);
        }

        // Default for anonymous users — overridden below for authenticated roles
        model.addAttribute("jobPost", jobPost);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            String email = authentication.getName();
            model.addAttribute("username", email);

            UserDto userDto = userServiceClient.getUserByEmail(email);

            if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("Recruiter"))) {
                List<RecruiterJobsDto> recruiterJobs = jobPostActivityService.getRecruiterJobs(
                        userDto != null ? userDto.userId() : 0);
                model.addAttribute("jobPost", recruiterJobs);
                RecruiterProfileDto profile = userDto != null
                        ? recruiterServiceClient.getRecruiterProfile(userDto.userId()) : null;
                model.addAttribute("user", profile);
            } else {
                int userId = userDto != null ? userDto.userId() : 0;
                List<Integer> appliedJobIds = applicationServiceClient.getApplicationsByJobSeeker(userId);
                List<Integer> savedJobIds = savedJobsServiceClient.getSavedJobsByJobSeeker(userId);

                for (JobPostActivity jobActivity : jobPost) {
                    jobActivity.setIsActive(appliedJobIds.contains(jobActivity.getJobPostId()));
                    jobActivity.setIsSaved(savedJobIds.contains(jobActivity.getJobPostId()));
                }
                model.addAttribute("jobPost", jobPost);
                JobSeekerProfileDto profile = userDto != null
                        ? jobSeekerServiceClient.getJobSeekerProfile(userDto.userId()) : null;
                model.addAttribute("user", profile);
            }
        }

        return "dashboard";
    }

    @GetMapping({"/global-search", "/global-search/"})
    public String globalSearch(Model model,
                               @RequestParam(value = "job", required = false) String job,
                               @RequestParam(value = "location", required = false) String location,
                               @RequestParam(value = "partTime", required = false) String partTime,
                               @RequestParam(value = "fullTime", required = false) String fullTime,
                               @RequestParam(value = "freelance", required = false) String freelance,
                               @RequestParam(value = "remoteOnly", required = false) String remoteOnly,
                               @RequestParam(value = "officeOnly", required = false) String officeOnly,
                               @RequestParam(value = "partialRemote", required = false) String partialRemote,
                               @RequestParam(value = "today", required = false) boolean today,
                               @RequestParam(value = "days7", required = false) boolean days7,
                               @RequestParam(value = "days30", required = false) boolean days30) {

        model.addAttribute("partTime", Objects.equals(partTime, "Part-Time"));
        model.addAttribute("fullTime", Objects.equals(fullTime, "Full-Time"));
        model.addAttribute("freelance", Objects.equals(freelance, "Freelance"));
        model.addAttribute("remoteOnly", Objects.equals(remoteOnly, "Remote-Only"));
        model.addAttribute("officeOnly", Objects.equals(officeOnly, "Office-Only"));
        model.addAttribute("partialRemote", Objects.equals(partialRemote, "Partial-Remote"));
        model.addAttribute("today", today);
        model.addAttribute("days7", days7);
        model.addAttribute("days30", days30);
        model.addAttribute("job", job);
        model.addAttribute("location", location);

        LocalDate searchDate = null;
        List<JobPostActivity> jobPost;
        boolean dateSearchFlag = true;
        boolean remote = true;
        boolean type = true;

        if (days30) searchDate = LocalDate.now().minusDays(30);
        else if (days7) searchDate = LocalDate.now().minusDays(7);
        else if (today) searchDate = LocalDate.now();
        else dateSearchFlag = false;

        if (partTime == null && fullTime == null && freelance == null) {
            partTime = "Part-Time"; fullTime = "Full-Time"; freelance = "Freelance"; remote = false;
        }
        if (officeOnly == null && remoteOnly == null && partialRemote == null) {
            officeOnly = "Office-Only"; remoteOnly = "Remote-Only"; partialRemote = "Partial-Remote"; type = false;
        }

        if (!dateSearchFlag && !remote && !type && !StringUtils.hasText(job) && !StringUtils.hasText(location)) {
            jobPost = jobPostActivityService.getAll();
        } else {
            jobPost = jobPostActivityService.search(job, location,
                    Arrays.asList(partTime, fullTime, freelance),
                    Arrays.asList(remoteOnly, officeOnly, partialRemote), searchDate);
        }

        model.addAttribute("jobPost", jobPost);
        return "global-search";
    }

    @GetMapping("/dashboard/add")
    public String addJobs(Model model) {
        model.addAttribute("jobPostActivity", new JobPostActivity());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof AnonymousAuthenticationToken)) {
            UserDto userDto = userServiceClient.getUserByEmail(auth.getName());
            if (userDto != null)
                model.addAttribute("user", recruiterServiceClient.getRecruiterProfile(userDto.userId()));
        }
        return "add-jobs";
    }

    @PostMapping("/dashboard/deleteJob/{id}")
    public String deleteJob(@PathVariable("id") int id, HttpServletRequest request) {
        jobPostActivityService.deleteById(id);
        return buildGatewayRedirectUrl(request, "dashboard/");
    }

    @PostMapping("/dashboard/addNew")
    public String addNew(JobPostActivity jobPostActivity,
                         @RequestParam(value = "companyLogo", required = false) MultipartFile companyLogo,
                         RedirectAttributes redirectAttributes,
                         HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof AnonymousAuthenticationToken)) {
            UserDto userDto = userServiceClient.getUserByEmail(auth.getName());
            if (userDto != null) jobPostActivity.setPostedById(userDto.userId());
        }
        jobPostActivity.setPostedDate(new Date());
        if (companyLogo != null && !companyLogo.isEmpty() && jobPostActivity.getJobCompanyId() != null) {
        	String ct = companyLogo.getContentType();
        	String fname = companyLogo.getOriginalFilename() != null ? companyLogo.getOriginalFilename().toLowerCase() : "";
            boolean validType = (ct != null && (ct.equalsIgnoreCase("image/jpeg") || ct.equalsIgnoreCase("image/png")))
                    || fname.endsWith(".jpg") || fname.endsWith(".jpeg") || fname.endsWith(".png");
            if (!validType) {
                redirectAttributes.addFlashAttribute("error", "Company logo must be a PNG or JPEG image.");
                return buildGatewayRedirectUrl(request, "dashboard/add");
            }

            try {
                String fileName = org.springframework.util.StringUtils.cleanPath(
                        java.util.Objects.requireNonNull(companyLogo.getOriginalFilename()));
                FileUploadUtil.saveFile("photos/company", fileName, companyLogo);
                jobPostActivity.getJobCompanyId().setLogo(fileName);
            } catch (IOException e) {
                // logo upload failed; job saved without logo
            }
        }
        jobPostActivityService.addNew(jobPostActivity);
        return buildGatewayRedirectUrl(request, "dashboard/");
    }

    @PostMapping("dashboard/edit/{id}")
    public String editJob(@PathVariable("id") int id, Model model) {
        JobPostActivity jobPostActivity = jobPostActivityService.getOne(id);
        model.addAttribute("jobPostActivity", jobPostActivity);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof AnonymousAuthenticationToken)) {
            UserDto userDto = userServiceClient.getUserByEmail(auth.getName());
            if (userDto != null)
                model.addAttribute("user", recruiterServiceClient.getRecruiterProfile(userDto.userId()));
        }
        return "add-jobs";
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
