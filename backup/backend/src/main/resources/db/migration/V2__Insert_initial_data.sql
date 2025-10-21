-- Insert initial data for ABService
-- Version: 1.0.0
-- Description: Insert default roles, permissions, and admin user

-- Insert default roles
INSERT INTO roles (id, name, description, is_active) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN', 'System Administrator with full access', true),
    ('00000000-0000-0000-0000-000000000002', 'USER', 'Regular user with basic access', true),
    ('00000000-0000-0000-0000-000000000003', 'MODERATOR', 'Moderator with limited administrative access', true),
    ('00000000-0000-0000-0000-000000000004', 'GUEST', 'Guest user with read-only access', true)
ON CONFLICT (id) DO NOTHING;

-- Insert default permissions
INSERT INTO permissions (id, name, description, resource, action, is_active) VALUES
    -- User management permissions
    ('00000000-0000-0000-0000-000000000101', 'USER_CREATE', 'Create new users', 'users', 'CREATE', true),
    ('00000000-0000-0000-0000-000000000102', 'USER_READ', 'Read user information', 'users', 'READ', true),
    ('00000000-0000-0000-0000-000000000103', 'USER_UPDATE', 'Update user information', 'users', 'UPDATE', true),
    ('00000000-0000-0000-0000-000000000104', 'USER_DELETE', 'Delete users', 'users', 'DELETE', true),
    
    -- Role management permissions
    ('00000000-0000-0000-0000-000000000201', 'ROLE_CREATE', 'Create new roles', 'roles', 'CREATE', true),
    ('00000000-0000-0000-0000-000000000202', 'ROLE_READ', 'Read role information', 'roles', 'READ', true),
    ('00000000-0000-0000-0000-000000000203', 'ROLE_UPDATE', 'Update role information', 'roles', 'UPDATE', true),
    ('00000000-0000-0000-0000-000000000204', 'ROLE_DELETE', 'Delete roles', 'roles', 'DELETE', true),
    
    -- Permission management permissions
    ('00000000-0000-0000-0000-000000000301', 'PERMISSION_CREATE', 'Create new permissions', 'permissions', 'CREATE', true),
    ('00000000-0000-0000-0000-000000000302', 'PERMISSION_READ', 'Read permission information', 'permissions', 'READ', true),
    ('00000000-0000-0000-0000-000000000303', 'PERMISSION_UPDATE', 'Update permission information', 'permissions', 'UPDATE', true),
    ('00000000-0000-0000-0000-000000000304', 'PERMISSION_DELETE', 'Delete permissions', 'permissions', 'DELETE', true),
    
    -- Audit log permissions
    ('00000000-0000-0000-0000-000000000401', 'AUDIT_READ', 'Read audit logs', 'audit_logs', 'READ', true),
    
    -- System administration permissions
    ('00000000-0000-0000-0000-000000000501', 'SYSTEM_CONFIG', 'Configure system settings', 'system', 'CONFIGURE', true),
    ('00000000-0000-0000-0000-000000000502', 'SYSTEM_MONITOR', 'Monitor system status', 'system', 'MONITOR', true)
ON CONFLICT (id) DO NOTHING;

-- Assign permissions to ADMIN role
INSERT INTO role_permissions (role_id, permission_id, granted_at) VALUES
    -- All user management permissions
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000101', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000102', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000103', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000104', CURRENT_TIMESTAMP),
    
    -- All role management permissions
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000201', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000202', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000203', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000204', CURRENT_TIMESTAMP),
    
    -- All permission management permissions
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000301', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000302', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000303', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000304', CURRENT_TIMESTAMP),
    
    -- Audit and system permissions
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000401', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000501', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000502', CURRENT_TIMESTAMP)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Assign permissions to MODERATOR role
INSERT INTO role_permissions (role_id, permission_id, granted_at) VALUES
    -- User read and update permissions
    ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000102', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000103', CURRENT_TIMESTAMP),
    
    -- Role and permission read permissions
    ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000202', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000302', CURRENT_TIMESTAMP),
    
    -- Audit read permission
    ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000401', CURRENT_TIMESTAMP)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Assign permissions to USER role
INSERT INTO role_permissions (role_id, permission_id, granted_at) VALUES
    -- User read permission (own profile)
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000102', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000103', CURRENT_TIMESTAMP)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Assign permissions to GUEST role
INSERT INTO role_permissions (role_id, permission_id, granted_at) VALUES
    -- Only read permissions
    ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000102', CURRENT_TIMESTAMP)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Create default admin user (password: admin123 - should be changed in production)
-- Note: This is a placeholder. In production, use proper password hashing
INSERT INTO users (id, username, email, password_hash, first_name, last_name, is_active, is_email_verified) VALUES
    ('00000000-0000-0000-0000-000000000001', 'admin', 'admin@abservice.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi', 'System', 'Administrator', true, true)
ON CONFLICT (id) DO NOTHING;

-- Assign ADMIN role to admin user
INSERT INTO user_roles (user_id, role_id, assigned_at) VALUES
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP)
ON CONFLICT (user_id, role_id) DO NOTHING;
