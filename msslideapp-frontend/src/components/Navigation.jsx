import React from 'react';

const Navigation = ({ currentPage, onNavigate }) => {
    const navItems = [
        { id: 'upload', label: 'Upload & Review', icon: '📤' },
        { id: 'pending', label: 'Pending Approvals', icon: '⏳' },
        { id: 'sent', label: 'Sent Approvals', icon: '📨' },
        { id: 'history', label: 'Commit History', icon: '📜' }
    ];

    return (
        <nav className="nav-sidebar">
            <div className="nav-logo">
                <h1>MsManager</h1>
            </div>
            <div className="nav-menu">
                {navItems.map(item => (
                    <div
                        key={item.id}
                        className={`nav-item ${currentPage === item.id ? 'active' : ''}`}
                        onClick={() => onNavigate(item.id)}
                    >
                        <span className="nav-icon">{item.icon}</span>
                        <span>{item.label}</span>
                    </div>
                ))}
            </div>
        </nav>
    );
};

export default Navigation;
