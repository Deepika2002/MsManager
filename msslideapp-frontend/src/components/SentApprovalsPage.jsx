import React, { useState, useEffect } from 'react';
import { api } from '../utils/api';
import ChangePreview from './ChangePreview';

const SentApprovalsPage = () => {
    const [approvals, setApprovals] = useState([]);
    const [loading, setLoading] = useState(true);
    const [selectedPRs, setSelectedPRs] = useState({});
    const [prChanges, setPrChanges] = useState({});
    const [loadingChanges, setLoadingChanges] = useState({});

    useEffect(() => {
        loadApprovals();
    }, []);

    const loadApprovals = async () => {
        setLoading(true);
        try {
            const data = await api.getSentApprovals();
            setApprovals(data);
        } catch (error) {
            console.error('Failed to load sent approvals:', error);
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

    const getStatusBadge = (state) => {
        if (state === 'open') {
            return <span className="badge badge-open">Open</span>;
        } else if (state === 'closed') {
            return <span className="badge badge-closed">Closed</span>;
        } else {
            return <span className="badge badge-merged">Merged</span>;
        }
    };

    if (loading) {
        return (
            <div className="fade-in">
                <div className="page-header">
                    <h1 className="page-title">Sent Approvals</h1>
                    <p className="page-subtitle">Track the status of your submitted changes</p>
                </div>
                <div className="spinner"></div>
            </div>
        );
    }

    return (
        <div className="fade-in">
            <div className="page-header">
                <h1 className="page-title">Sent Approvals</h1>
                <p className="page-subtitle">Track the status of your submitted changes</p>
            </div>

            {approvals.length === 0 ? (
                <div className="card">
                    <div className="empty-state">
                        <div className="empty-state-icon">📭</div>
                        <div className="empty-state-text">No sent approvals yet</div>
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
                                {getStatusBadge(pr.state)}
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
                                {selectedPRs[pr.number] ? '🔽 Hide Details' : '🔍 View Details'}
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

                            <a
                                href={pr.html_url}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="btn btn-secondary"
                            >
                                🔗 View on GitHub
                            </a>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default SentApprovalsPage;
