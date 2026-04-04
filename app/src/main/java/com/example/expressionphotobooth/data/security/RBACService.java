package com.example.expressionphotobooth.data.security;

import com.example.expressionphotobooth.domain.model.UserRole;

/**
 * Role-Based Access Control (RBAC) service
 * Manages permission checking for different user roles
 * Roles: ADMIN, PREMIUM, USER, GUEST
 */
public class RBACService {
    private final String currentUserId;
    private final UserRole userRole;
    private final boolean isGuest;

    public RBACService(String userId, UserRole role, boolean isGuest) {
        this.currentUserId = userId;
        this.userRole = role != null ? role : UserRole.USER;
        this.isGuest = isGuest;
    }

    /**
     * Check if user can view own history
     */
    public boolean canViewOwnHistory() {
        return !isGuest && userRole != null;
    }

    /**
     * Check if user can view others' history
     * Only ADMIN can view other users' history
     */
    public boolean canViewOthersHistory() {
        return userRole == UserRole.ADMIN;
    }

    /**
     * Check if user can decrypt and download image
     * Must own the image or be admin
     */
    public boolean canDecryptImage(String ownerUserId) {
        if (isGuest) {
            return false;
        }
        // User can decrypt own images, ADMIN can decrypt any image
        return currentUserId.equals(ownerUserId) || userRole == UserRole.ADMIN;
    }

    /**
     * Check if user can access specific session
     * Must own the session or be admin
     */
    public boolean canAccessSession(String sessionOwnerId) {
        if (isGuest) {
            return false;
        }
        return currentUserId.equals(sessionOwnerId) || userRole == UserRole.ADMIN;
    }

    /**
     * Check if user can delete image
     * Can only delete own images, admins can delete any
     */
    public boolean canDeleteImage(String imageOwnerId) {
        if (isGuest) {
            return false;
        }
        return currentUserId.equals(imageOwnerId) || userRole == UserRole.ADMIN;
    }

    /**
     * Check if user can edit image metadata
     * Can only edit own images, admins can edit any
     */
    public boolean canEditImageMetadata(String imageOwnerId) {
        if (isGuest) {
            return false;
        }
        return currentUserId.equals(imageOwnerId) || userRole == UserRole.ADMIN;
    }

    /**
     * Check if user can access admin panel
     * Only ADMIN role can access admin functions
     */
    public boolean canAccessAdminPanel() {
        return userRole == UserRole.ADMIN;
    }

    /**
     * Check if user has premium features
     */
    public boolean hasPremiumFeatures() {
        return userRole == UserRole.PREMIUM || userRole == UserRole.ADMIN;
    }

    /**
     * Get user permission level
     * Higher = more permissions
     */
    public int getPermissionLevel() {
        if (isGuest) return 0;
        switch (userRole) {
            case ADMIN:
                return 3;
            case PREMIUM:
                return 2;
            case USER:
                return 1;
            default:
                return 0;
        }
    }

    /**
     * Check if user has at least minimum permission level
     */
    public boolean hasMinimumPermissionLevel(int minimumLevel) {
        return getPermissionLevel() >= minimumLevel;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public UserRole getUserRole() {
        return userRole;
    }

    public boolean isGuest() {
        return isGuest;
    }
}

