package com.abservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * CircleMember更新用のデータ転送オブジェクト
 */
public class UpdateCircleMemberDto {

    @Size(max = 100, message = "表示名は100文字以内で入力してください")
    private String displayName;

    @Email(message = "有効なメールアドレスを入力してください")
    @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
    private String email;

    @Size(max = 500, message = "自己紹介は500文字以内で入力してください")
    private String bio;

    @Size(max = 255, message = "アバターURLは255文字以内で入力してください")
    private String avatarUrl;

    private Boolean isActive;

    private Long roleId;

    public UpdateCircleMemberDto() {}

    public UpdateCircleMemberDto(String displayName, String email, String bio,
                                String avatarUrl, Boolean isActive, Long roleId) {
        this.displayName = displayName;
        this.email = email;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.isActive = isActive;
        this.roleId = roleId;
    }

    // Getters and Setters
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    @Override
    public String toString() {
        return "UpdateCircleMemberDto{" +
                "displayName='" + displayName + '\'' +
                ", email='" + email + '\'' +
                ", bio='" + bio + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", isActive=" + isActive +
                ", roleId=" + roleId +
                '}';
    }
}


