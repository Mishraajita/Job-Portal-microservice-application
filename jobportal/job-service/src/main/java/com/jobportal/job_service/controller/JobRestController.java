package com.jobportal.job_service.controller;

import com.jobportal.job_service.dto.JobDto;
import com.jobportal.job_service.entity.JobPostActivity;
import com.jobportal.job_service.services.JobPostActivityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobRestController {

    private final JobPostActivityService jobPostActivityService;

    public JobRestController(JobPostActivityService jobPostActivityService) {
        this.jobPostActivityService = jobPostActivityService;
    }

    // Called by application-service and saved-jobs-service
    @GetMapping("/{id}")
    public ResponseEntity<JobDto> getJobById(@PathVariable int id) {
        JobPostActivity job = jobPostActivityService.getOne(id);
        JobDto dto = new JobDto(
                job.getJobPostId(),
                job.getJobTitle(),
                job.getJobType(),
                job.getRemote(),
                job.getSalary(),
                job.getPostedDate(),
                job.getDescriptionOfJob(),
                job.getJobLocationId() != null
                        ? new JobDto.LocationDto(job.getJobLocationId().getId(),
                                job.getJobLocationId().getCity(),
                                job.getJobLocationId().getState(),
                                job.getJobLocationId().getCountry())
                        : null,
                job.getJobCompanyId() != null
                        ? new JobDto.CompanyDto(job.getJobCompanyId().getId(),
                                job.getJobCompanyId().getName(),
                        		job.getJobCompanyId().getLogo())
                        : null,
                job.getIsActive()        
        );
        return ResponseEntity.ok(dto);
    }
}
