package com.jobportal.application_service.dto;

public record JobSeekerProfileDto(int userAccountId, String firstName, String lastName,
                                  String city, String state, String country,
                                  String photosImagePath, String resume) {}
