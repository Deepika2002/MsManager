import React, { useState, useEffect } from 'react';
import { api } from '../utils/api';
import ChangePreview from './ChangePreview';

const PendingApprovalsPage = () => {
    const [approvals, setApprovals] = useState([]);
    const [loading, setLoading] = useState(true);
    const [selectedPRs, setSelectedPRs] = useState({});
    const [prChanges, setPrChanges] = useState({});
    const [loadingChanges, setLoadingChanges] = useState({});
    const [comments, setComments] = useState({});
    const [processing, setProcessing] = useState(false);

    useEffect(() => {
        loadApprovals();
    }, []);

    const loadApprovals = async () => {
        setLoading(true);
        try {
            const data = await api.getPendingApprovals();
            if (Array.isArray(data)) {
                setApprovals(data);
            } else {
                console.error('getPendingApprovals did not return an array:', data);
                setApprovals([]);
            }
        } catch (error) {
            console.error('Failed to load approvals:', error);
            setApprovals([]);
        } finally {
            setLoading(false);
        }
    };

    const togglePR = async (prNumber) => {
        const isCurrentlyOpen = selectedPRs[prNumber];

        setSelectedPRs(prev => ({
            ...prev,
            [prNumber]: !prev[prNumber]
        }));

        // Load changes if opening and not already loaded
        if (!isCurrentlyOpen && !prChanges[prNumber]) {
            setLoadingChanges(prev => ({ ...prev, [prNumber]: true }));
            try {
                const changes = await api.getPRChanges(prNumber);
                setPrChanges(prev => ({ ...prev, [prNumber]: changes }));
            } catch (error) {
                console.error('Failed to load PR changes:', error);
                setPrChanges(prev => ({ ...prev, [prNumber]: [] }));
            } finally {
                setLoadingChanges(prev => ({ ...prev, [prNumber]: false }));
            }
        }
    };

    const handleApprove = async (prNumber) => {
        // Removed window.confirm to fix button responsiveness
        setProcessing(true);
        try {
            await api.approvePR(prNumber, comments[prNumber] || '');
            alert('Changes approved successfully!');
            setComments(prev => ({ ...prev, [prNumber]: '' }));
            loadApprovals();
        } catch (error) {
            console.error('Approval failed:', error);
            alert('Approval failed: ' + (error.response?.data?.message || error.message));
        } finally {
            setProcessing(false);
        }
    };

    const handleReject = async (prNumber) => {
        const comment = comments[prNumber];
        if (!comment || !comment.trim()) {
            alert('Please provide a reason for rejection');
            return;
        }

        // Removed window.confirm to fix button responsiveness
        setProcessing(true);
        try {
            await api.rejectPR(prNumber, comment);
            alert('Changes rejected successfully!');
            setComments(prev => ({ ...prev, [prNumber]: '' }));
            loadApprovals();
        } catch (error) {
            console.error('Rejection failed:', error);
            alert('Rejection failed: ' + (error.response?.data?.message || error.message));
        } finally {
            setProcessing(false);
        }
    };

    const updateComment = (prNumber, value) => {
        setComments(prev => ({ ...prev, [prNumber]: value }));
    };

    if (loading) {
        return (
            <div className="fade-in">
                <div className="page-header">
                    <h1 className="page-title">Pending Approvals</h1>
                    <p className="page-subtitle">Review and approve changes waiting for your approval</p>
                </div>
                <div className="spinner"></div>
            </div>
        );
    }

    return (
        <div className="fade-in">
            <div className="page-header">
                <h1 className="page-title">Pending Approvals</h1>
                <p className="page-subtitle">Review and approve changes waiting for your approval</p>
            </div>

            {approvals.length === 0 ? (
                <div className="card">
                    <div className="empty-state">
                        <div className="empty-state-icon">✅</div>
                        <div className="empty-state-text">No pending approvals</div>
                    </div>
                </div>
            ) : (
                <div style={{ display: 'grid', gap: '1.5rem' }}>
                    {approvals.map(pr => (
                        <div key={pr.number} className="card">
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', marginBottom: '1rem' }}>
                                <div>
                                    <h3 className="card-title" style={{ marginBottom: '0.5rem' }}>
                                        #{pr.number} {pr.title}
                                    </h3>
                                    <div style={{ fontSize: '0.875rem', color: '#666' }}>
                                        By {pr.user} • {new Date(pr.created_at).toLocaleDateString()}
                                    </div>
                                </div>
                                <span className="badge badge-open">Open</span>
                            </div>

                            {pr.body && (
                                <div style={{ marginBottom: '1rem', padding: '1rem', background: 'rgba(39, 119, 119, 0.05)', borderRadius: '8px' }}>
                                    <strong>Description:</strong>
                                    <p style={{ marginTop: '0.5rem', whiteSpace: 'pre-wrap' }}>{pr.body}</p>
                                </div>
                            )}

                            <button
                                className="btn btn-secondary"
                                onClick={() => togglePR(pr.number)}
                                style={{ marginBottom: '1rem' }}
                            >
                                {selectedPRs[pr.number] ? '🔽 Hide Changes' : '🔍 View Changes'}
                            </button>

                            {selectedPRs[pr.number] && (
                                <div style={{ marginBottom: '1rem' }}>
                                    {loadingChanges[pr.number] ? (
                                        <div className="spinner" style={{ margin: '2rem auto' }}></div>
                                    ) : (
                                        <ChangePreview changes={prChanges[pr.number] || []} />
                                    )}
                                </div>
                            )}

                            <div className="form-group">
                                <label className="form-label">Comment (Optional for approval, Required for rejection)</label>
                                <textarea
                                    className="form-textarea"
                                    placeholder="Add your review comments..."
                                    value={comments[pr.number] || ''}
                                    onChange={(e) => updateComment(pr.number, e.target.value)}
                                />
                            </div>

                            <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
                                <button
                                    className="btn btn-success"
                                    onClick={() => handleApprove(pr.number)}
                                    disabled={processing}
                                >
                                    ✓ Approve
                                </button>
                                <button
                                    className="btn btn-danger"
                                    onClick={() => handleReject(pr.number)}
                                    disabled={processing || !(comments[pr.number] && comments[pr.number].trim())}
                                >
                                    ✗ Reject
                                </button>
                                <a
                                    href={pr.html_url}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="btn btn-secondary"
                                >
                                    🔗 View on GitHub
                                </a>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default PendingApprovalsPage;
