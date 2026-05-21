package com.jobportal.job_service.dto;

import java.util.Date;

// Lightweight DTO returned by job-service REST API for inter-service consumption
public record JobDto(
        Integer jobPostId,
        String jobTitle,
        String jobType,
        String remote,
        String salary,
        Date postedDate,
        String descriptionOfJob,
        LocationDto jobLocationId,
        CompanyDto jobCompanyId,
        Boolean isActive
) {
    public record LocationDto(Integer id, String city, String state, String country) {}
    public record CompanyDto(Integer id, String name, String logo) {}
}
