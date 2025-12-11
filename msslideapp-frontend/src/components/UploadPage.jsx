import React, { useState, useEffect } from 'react';
import { api } from '../utils/api';
import ChangePreview from './ChangePreview';

const UploadPage = () => {
    const [files, setFiles] = useState([]);
    const [commitMessage, setCommitMessage] = useState('');
    const [collaborators, setCollaborators] = useState([]);
    const [selectedApprovers, setSelectedApprovers] = useState([]);
    const [showDropdown, setShowDropdown] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [changes, setChanges] = useState(null);
    const [dragOver, setDragOver] = useState(false);

    useEffect(() => {
        loadCollaborators();
    }, []);

    const loadCollaborators = async () => {
        try {
            const data = await api.getCollaborators();
            setCollaborators(data);
        } catch (error) {
            console.error('Failed to load collaborators:', error);
        }
    };

    const handleFileChange = (e) => {
        const selectedFiles = Array.from(e.target.files);
        if (selectedFiles.length > 0) {
            setFiles(prev => [...prev, ...selectedFiles]);
            setChanges(null);
        }
    };

    const handleDrop = (e) => {
        e.preventDefault();
        setDragOver(false);
        const droppedFiles = Array.from(e.dataTransfer.files).filter(f =>
            f.name.endsWith('.xlsx') || f.name.endsWith('.xls')
        );

        if (droppedFiles.length > 0) {
            setFiles(prev => [...prev, ...droppedFiles]);
            setChanges(null);
        }
    };

    const handleDragOver = (e) => {
        e.preventDefault();
        setDragOver(true);
    };

    const handleDragLeave = () => {
        setDragOver(false);
    };

    const removeFile = (index) => {
        setFiles(prev => prev.filter((_, i) => i !== index));
    };

    const toggleApprover = (login) => {
        setSelectedApprovers(prev =>
            prev.includes(login)
                ? prev.filter(a => a !== login)
                : [...prev, login]
        );
    };

    const removeApprover = (login) => {
        setSelectedApprovers(prev => prev.filter(a => a !== login));
    };

    const handleUpload = async () => {
        if (files.length === 0) {
            alert('Please select at least one file');
            return;
        }

        if (!commitMessage.trim()) {
            alert('Please enter a Jira ticket ID / commit message');
            return;
        }

        setUploading(true);
        try {
            const response = await api.uploadFile(files, commitMessage, selectedApprovers);
            setChanges(response.changes);

            // Clear form
            setFiles([]);
            setCommitMessage('');
            setSelectedApprovers([]);

            // Show success message with navigation option
            const viewApprovals = window.confirm(
                '✅ Changes successfully sent for approval!\n\n' +
                'Your Pull Request has been created.\n' +
                (selectedApprovers.length > 0
                    ? `Reviewers: ${selectedApprovers.join(', ')}\n`
                    : 'No reviewers assigned.\n') +
                '\nClick OK to view Sent Approvals, or Cancel to stay here.'
            );

            if (viewApprovals) {
                window.location.href = '/sent-approvals';
            }
        } catch (error) {
            console.error('Upload failed:', error);
            alert('Upload failed: ' + (error.response?.data?.message || error.message));
        } finally {
            setUploading(false);
        }
    };

    return (
        <div className="fade-in">
            <div className="page-header">
                <h1 className="page-title">Upload & Review</h1>
                <p className="page-subtitle">Upload your Excel files and review changes before committing</p>
            </div>

            <div className="card" style={{ marginBottom: '2rem' }}>
                <h2 className="card-title">Upload Files</h2>

                <div
                    className={`file-upload-area ${dragOver ? 'drag-over' : ''}`}
                    onDrop={handleDrop}
                    onDragOver={handleDragOver}
                    onDragLeave={handleDragLeave}
                    onClick={() => document.getElementById('file-input').click()}
                >
                    <div className="file-upload-icon">📁</div>
                    <div className="file-upload-text">
                        Click to browse or drag and drop your Excel files here
                    </div>
                    <div className="file-upload-hint">Supported formats: .xlsx, .xls</div>
                    <input
                        id="file-input"
                        type="file"
                        multiple
                        accept=".xlsx,.xls"
                        onChange={handleFileChange}
                        style={{ display: 'none' }}
                    />
                </div>

                {files.length > 0 && (
                    <div className="file-list" style={{ marginTop: '1rem' }}>
                        {files.map((f, index) => (
                            <div key={index} className="file-item" style={{ display: 'flex', alignItems: 'center', padding: '0.5rem', background: '#f9fafb', borderRadius: '4px', marginBottom: '0.5rem' }}>
                                <span style={{ marginRight: '0.5rem' }}>📄</span>
                                <span style={{ flex: 1 }}>{f.name}</span>
                                <button
                                    onClick={(e) => { e.stopPropagation(); removeFile(index); }}
                                    style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer', fontSize: '1.2rem' }}
                                >
                                    ×
                                </button>
                            </div>
                        ))}
                    </div>
                )}

                <div className="form-group" style={{ marginTop: '1.5rem' }}>
                    <label className="form-label">Jira Ticket ID / Commit Message *</label>
                    <input
                        type="text"
                        className="form-input"
                        placeholder="e.g., JIRA-1234 or Update master slides"
                        value={commitMessage}
                        onChange={(e) => setCommitMessage(e.target.value)}
                    />
                </div>

                <div className="form-group">
                    <label className="form-label">Select Approvers (Optional)</label>
                    <div className="multi-select">
                        <div
                            className="multi-select-selected"
                            onClick={() => setShowDropdown(!showDropdown)}
                        >
                            {selectedApprovers.length === 0 ? (
                                <span style={{ color: '#999' }}>Click to select approvers...</span>
                            ) : (
                                selectedApprovers.map(login => (
                                    <span key={login} className="multi-select-tag">
                                        {login}
                                        <span
                                            className="multi-select-tag-remove"
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                removeApprover(login);
                                            }}
                                        >
                                            ×
                                        </span>
                                    </span>
                                ))
                            )}
                        </div>
                        {showDropdown && (
                            <div className="multi-select-dropdown">
                                {collaborators.map(collab => (
                                    <div
                                        key={collab.login}
                                        className={`multi-select-option ${selectedApprovers.includes(collab.login) ? 'selected' : ''}`}
                                        onClick={() => toggleApprover(collab.login)}
                                    >
                                        {collab.login}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>

                <button
                    className="btn btn-primary"
                    onClick={handleUpload}
                    disabled={uploading || files.length === 0 || !commitMessage.trim()}
                >
                    {uploading ? '⏳ Uploading...' : '✓ Commit Changes'}
                </button>
            </div>

            {changes && (
                <div className="card">
                    <h2 className="card-title">Change Preview ({changes.length} changes)</h2>
                    <ChangePreview changes={changes} />
                </div>
            )}
        </div>
    );
};

export default UploadPage;
