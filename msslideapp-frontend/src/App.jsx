import React from 'react';
import { BrowserRouter as Router, Routes, Route, Link, useLocation } from 'react-router-dom';
import UploadPage from './components/UploadPage';
import PendingApprovalsPage from './components/PendingApprovalsPage';
import SentApprovalsPage from './components/SentApprovalsPage';
import CommitHistoryPage from './components/CommitHistoryPage';
import './styles.css';

const Navigation = () => {
    const location = useLocation();

    return (
        <nav className="navbar">
            <div className="nav-brand">MsManager</div>
            <div className="nav-links">
                <Link to="/" className={`nav-link ${location.pathname === '/' ? 'active' : ''}`}>
                    Upload & Review
                </Link>
                <Link to="/pending-approvals" className={`nav-link ${location.pathname === '/pending-approvals' ? 'active' : ''}`}>
                    Pending Approvals
                </Link>
                <Link to="/sent-approvals" className={`nav-link ${location.pathname === '/sent-approvals' ? 'active' : ''}`}>
                    Sent Approvals
                </Link>
                <Link to="/commit-history" className={`nav-link ${location.pathname === '/commit-history' ? 'active' : ''}`}>
                    Commit History
                </Link>
            </div>
        </nav>
    );
};

const App = () => {
    return (
        <Router>
            <div className="app-container">
                <Navigation />
                <main className="main-content">
                    <Routes>
                        <Route path="/" element={<UploadPage />} />
                        <Route path="/pending-approvals" element={<PendingApprovalsPage />} />
                        <Route path="/sent-approvals" element={<SentApprovalsPage />} />
                        <Route path="/commit-history" element={<CommitHistoryPage />} />
                    </Routes>
                </main>
            </div>
        </Router>
    );
};

export default App;
