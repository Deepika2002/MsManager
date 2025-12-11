import React, { useState, useEffect } from 'react';
import { api } from '../utils/api';
import ChangePreview from './ChangePreview';

const CommitHistoryPage = () => {
    const [commits, setCommits] = useState([]);
    const [filteredCommits, setFilteredCommits] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedCommit, setSelectedCommit] = useState(null);
    const [commitDetails, setCommitDetails] = useState(null);
    const [commitChanges, setCommitChanges] = useState(null);
    const [loadingDetails, setLoadingDetails] = useState(false);

    useEffect(() => {
        loadCommits();
    }, []);

    useEffect(() => {
        if (searchQuery.trim()) {
            const filtered = commits.filter(commit =>
                commit.message.toLowerCase().includes(searchQuery.toLowerCase())
            );
            setFilteredCommits(filtered);
        } else {
            setFilteredCommits(commits);
        }
    }, [searchQuery, commits]);

    const loadCommits = async () => {
        setLoading(true);
        try {
            const data = await api.getCommits();
            setCommits(data);
            setFilteredCommits(data);
        } catch (error) {
            console.error('Failed to load commits:', error);
        } finally {
            setLoading(false);
        }
    };

    const loadCommitDetails = async (sha) => {
        if (selectedCommit === sha) {
            setSelectedCommit(null);
            setCommitDetails(null);
            setCommitChanges(null);
            return;
        }

        setSelectedCommit(sha);
        setLoadingDetails(true);
        try {
            const details = await api.getCommitDetails(sha);
            const changes = await api.getCommitChanges(sha);
            setCommitDetails(details);
            setCommitChanges(changes);
        } catch (error) {
            console.error('Failed to load commit details:', error);
            setCommitDetails(null);
            setCommitChanges(null);
        } finally {
            setLoadingDetails(false);
        }
    };

    if (loading) {
        return (
            <div className="fade-in">
                <div className="page-header">
                    <h1 className="page-title">Commit History</h1>
                    <p className="page-subtitle">Browse and search through all commits</p>
                </div>
                <div className="spinner"></div>
            </div>
        );
    }

    return (
        <div className="fade-in">
            <div className="page-header">
                <h1 className="page-title">Commit History</h1>
                <p className="page-subtitle">Browse and search through all commits</p>
            </div>

            <div className="card" style={{ marginBottom: '2rem' }}>
                <div className="search-box">
                    <span className="search-icon">🔍</span>
                    <input
                        type="text"
                        className="search-input"
                        placeholder="Search commits by message (e.g., Jira ticket ID)..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                    />
                </div>
            </div>

            {filteredCommits.length === 0 ? (
                <div className="card">
                    <div className="empty-state">
                        <div className="empty-state-icon">🔍</div>
                        <div className="empty-state-text">
                            {searchQuery ? 'No commits found matching your search' : 'No commits yet'}
                        </div>
                    </div>
                </div>
            ) : (
                <div style={{ display: 'grid', gap: '1rem' }}>
                    {filteredCommits.map(commit => (
                        <div key={commit.sha} className="card">
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', marginBottom: '0.75rem' }}>
                                <div style={{ flex: 1 }}>
                                    <h3 style={{ fontSize: '1.125rem', fontWeight: 600, marginBottom: '0.5rem', color: 'var(--color-teal)' }}>
                                        {commit.message}
                                    </h3>
                                    <div style={{ fontSize: '0.875rem', color: '#666' }}>
                                        <span>By {commit.author}</span>
                                        <span style={{ margin: '0 0.5rem' }}>•</span>
                                        <span>{new Date(commit.date).toLocaleString()}</span>
                                        <span style={{ margin: '0 0.5rem' }}>•</span>
                                        <span style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                                            {commit.sha.substring(0, 7)}
                                        </span>
                                    </div>
                                </div>
                            </div>

                            <div style={{ display: 'flex', gap: '1rem' }}>
                                <button
                                    className="btn btn-secondary"
                                    onClick={() => loadCommitDetails(commit.sha)}
                                    style={{ fontSize: '0.875rem' }}
                                >
                                    {selectedCommit === commit.sha ? '🔽 Hide Details' : '🔍 View Details'}
                                </button>
                                <a
                                    href={commit.url}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="btn btn-secondary"
                                    style={{ fontSize: '0.875rem' }}
                                >
                                    🔗 View on GitHub
                                </a>
                            </div>

                            {selectedCommit === commit.sha && (
                                <div style={{ marginTop: '1.5rem', paddingTop: '1.5rem', borderTop: '2px solid rgba(39, 119, 119, 0.1)' }}>
                                    {loadingDetails ? (
                                        <div className="spinner" style={{ margin: '2rem auto' }}></div>
                                    ) : commitDetails ? (
                                        <div>
                                            <h4 style={{ marginBottom: '1rem', color: 'var(--color-teal)' }}>
                                                Files Changed
                                            </h4>
                                            {commitDetails.files && commitDetails.files.length > 0 ? (
                                                <div>
                                                    <div className="changes-table-container">
                                                        <table className="changes-table">
                                                            <thead>
                                                                <tr>
                                                                    <th>File</th>
                                                                    <th>Status</th>
                                                                    <th>Additions</th>
                                                                    <th>Deletions</th>
                                                                </tr>
                                                            </thead>
                                                            <tbody>
                                                                {commitDetails.files.map((file, index) => (
                                                                    <tr key={index}>
                                                                        <td style={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>
                                                                            {file.filename}
                                                                        </td>
                                                                        <td>
                                                                            <span
                                                                                className="type-badge"
                                                                                style={{
                                                                                    backgroundColor:
                                                                                        file.status === 'added' ? '#10b981' :
                                                                                            file.status === 'removed' ? '#ef4444' :
                                                                                                '#f59e0b'
                                                                                }}
                                                                            >
                                                                                {file.status}
                                                                            </span>
                                                                        </td>
                                                                        <td style={{ color: 'var(--color-mint)', fontWeight: 600 }}>
                                                                            +{file.additions}
                                                                        </td>
                                                                        <td style={{ color: '#ef4444', fontWeight: 600 }}>
                                                                            -{file.deletions}
                                                                        </td>
                                                                    </tr>
                                                                ))}
                                                            </tbody>
                                                        </table>
                                                    </div>

                                                    {commitChanges && commitChanges.length > 0 && (
                                                        <div style={{ marginTop: '1.5rem' }}>
                                                            <h5 style={{ marginBottom: '0.75rem', color: 'var(--color-teal)' }}>
                                                                Cell-Level Changes
                                                            </h5>
                                                            <ChangePreview changes={commitChanges} />
                                                        </div>
                                                    )}
                                                </div>
                                            ) : (
                                                <div className="empty-state">
                                                    <div className="empty-state-text">No file changes in this commit</div>
                                                </div>
                                            )}
                                        </div>
                                    ) : (
                                        <div className="empty-state">
                                            <div className="empty-state-text">Failed to load commit details</div>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default CommitHistoryPage;
