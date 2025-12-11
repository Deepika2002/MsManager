import React, { useState, useMemo, useEffect } from 'react';

export default function ChangePreview({ changes }) {
    const [selectedFilter, setSelectedFilter] = useState('All');

    // Expansion State
    const [expandedFiles, setExpandedFiles] = useState(new Set());
    const [expandedSheets, setExpandedSheets] = useState(new Set());

    // Pagination State: Map key ("fileName|sheetName") -> number of visible rows
    const [visibleCounts, setVisibleCounts] = useState({});
    const PAGE_SIZE = 10;

    // Safe normalize changes
    const [safeChanges, setSafeChanges] = useState([]);

    useEffect(() => {
        if (!changes) {
            setSafeChanges([]);
            return;
        }
        if (Array.isArray(changes)) {
            setSafeChanges(changes);
        } else if (changes.changes && Array.isArray(changes.changes)) {
            // Handle case where API response might be wrapped in an object
            setSafeChanges(changes.changes);
        } else {
            console.warn('ChangePreview received invalid changes format:', changes);
            setSafeChanges([]);
        }
    }, [changes]);

    // Initialize state when changes change
    useEffect(() => {
        if (!safeChanges || safeChanges.length === 0) return;

        const files = new Set();
        const sheets = new Set();
        const counts = {};

        safeChanges.forEach(c => {
            const fName = c.fileName || 'Unknown File';
            const sName = c.sheet || 'Unknown Sheet';
            const key = `${fName}|${sName}`;

            files.add(fName);
            sheets.add(key);
            if (!counts[key]) counts[key] = PAGE_SIZE;
        });

        // Expand all by default if there are few files (e.g. < 5)
        if (files.size < 5) {
            setExpandedFiles(files);
            setExpandedSheets(sheets);
        } else {
            // Otherwise just expand the first file
            const firstFile = safeChanges[0]?.fileName || 'Unknown File';
            setExpandedFiles(new Set([firstFile]));
            // Expand sheets of first file
            const firstFileSheets = safeChanges.filter(c => (c.fileName || 'Unknown File') === firstFile)
                .map(c => `${firstFile}|${c.sheet || 'Unknown Sheet'}`);
            setExpandedSheets(new Set(firstFileSheets));
        }

        setVisibleCounts(counts);
    }, [safeChanges]);

    // Group and Filter changes
    const { hierarchy, summary } = useMemo(() => {
        const hierarchy = {}; // { fileName: { sheetName: [changes] } }
        const summary = { total: 0, modified: 0, added: 0, deleted: 0 };

        if (!safeChanges) return { hierarchy, summary };

        // 1. Global Summary
        safeChanges.forEach(c => {
            summary.total++;
            if (c.changeType === 'MODIFIED') summary.modified++;
            if (c.changeType === 'ADDED') summary.added++;
            if (c.changeType === 'DELETED') summary.deleted++;
        });

        // 2. Filtered Hierarchy
        safeChanges.forEach(change => {
            if (selectedFilter !== 'All' && change.changeType !== selectedFilter.toUpperCase()) {
                return;
            }

            const fileName = change.fileName || 'Unknown File';
            const sheetName = change.sheet || 'Unknown Sheet';

            if (!hierarchy[fileName]) hierarchy[fileName] = {};
            if (!hierarchy[fileName][sheetName]) hierarchy[fileName][sheetName] = [];

            hierarchy[fileName][sheetName].push(change);
        });

        return { hierarchy, summary };
    }, [safeChanges, selectedFilter]);

    // ... (helper functions same)

    const toggleFile = (fileName) => {
        const newSet = new Set(expandedFiles);
        if (newSet.has(fileName)) newSet.delete(fileName);
        else newSet.add(fileName);
        setExpandedFiles(newSet);
    };

    const toggleSheet = (key) => {
        const newSet = new Set(expandedSheets);
        if (newSet.has(key)) newSet.delete(key);
        else newSet.add(key);
        setExpandedSheets(newSet);
    };

    const showMore = (key) => {
        setVisibleCounts(prev => ({
            ...prev,
            [key]: (prev[key] || 0) + PAGE_SIZE
        }));
    };

    // Helper functions
    const formatCellValue = (value, meta, isOld) => {
        if (!value) return <span className="empty-cell">—</span>;

        const fontColor = isOld ? meta?.oldFontColor : meta?.newFontColor;
        const bgColor = isOld ? meta?.oldBgColor : meta?.newBgColor;
        const isBold = isOld ? meta?.oldBold : meta?.newBold;
        const isStrike = isOld ? meta?.oldStrike : meta?.newStrike;

        const style = {
            color: fontColor || '#000000',
            backgroundColor: bgColor || 'transparent',
            fontWeight: isBold ? 'bold' : 'normal',
            textDecoration: isStrike ? 'line-through' : 'none',
            padding: '4px 8px',
            borderRadius: '2px',
            display: 'inline-block',
            minWidth: '60px'
        };

        return <span style={style}>{value}</span>;
    };

    const getTypeColor = (type) => {
        switch (type) {
            case 'ADDED': return '#10b981';
            case 'DELETED': return '#ef4444';
            case 'MODIFIED': return '#f59e0b';
            default: return '#6b7280';
        }
    };

    const getColorName = (hex) => {
        if (!hex) return null;
        const normalizedHex = hex.toUpperCase();
        const colors = {
            '#000000': 'Black',
            '#FFFFFF': 'White',
            '#FF0000': 'Red',
            '#00FF00': 'Green',
            '#0000FF': 'Blue',
            '#FFFF00': 'Yellow',
            '#FFA500': 'Orange',
            '#800080': 'Purple',
            '#808080': 'Gray',
            '#C0C0C0': 'Silver',
            '#FFC0CB': 'Pink',
            '#A52A2A': 'Brown',
            '#00FFFF': 'Cyan',
            '#FF00FF': 'Magenta'
        };
        return colors[normalizedHex] || hex;
    };

    const generateChangeDescription = (change) => {
        const parts = [];
        const { meta, changeType, oldValue, newValue } = change;

        if (changeType === 'ADDED') return 'New cell added';
        if (changeType === 'DELETED') return 'Cell deleted';

        // Value Change
        if (oldValue !== newValue) {
            parts.push('Value updated');
        }

        // Style Changes
        if (meta) {
            if (meta.oldFontColor !== meta.newFontColor) {
                const newColor = getColorName(meta.newFontColor) || 'Default';
                parts.push(`Font color: ${newColor}`);
            }
            if (meta.oldBgColor !== meta.newBgColor) {
                const newBg = getColorName(meta.newBgColor) || 'None';
                parts.push(`Background: ${newBg}`);
            }
            if (meta.oldBold !== meta.newBold) {
                parts.push(meta.newBold ? 'Made Bold' : 'Un-bolded');
            }
            if (meta.oldStrike !== meta.newStrike) {
                parts.push(meta.newStrike ? 'Strikethrough added' : 'Strikethrough removed');
            }
        }

        if (parts.length === 0) return 'No visible change';
        return parts.join(', ');
    };

    // Render Clean
    if (!safeChanges || safeChanges.length === 0) {
        return (
            <div className="change-preview empty">
                <p>No changes detected</p>
            </div>
        );
    }

    const fileNames = Object.keys(hierarchy).sort();

    return (
        <div className="change-preview">
            {/* Summary Header */}
            <div className="change-summary">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                    <h3>Summary</h3>
                    {/* Filter Buttons */}
                    <div className="change-filter" style={{ marginBottom: 0 }}>
                        {['All', 'Modified', 'Added', 'Deleted'].map(f => (
                            <button
                                key={f}
                                className={selectedFilter === f ? 'active' : ''}
                                onClick={() => setSelectedFilter(f)}
                            >
                                {f}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="summary-stats">
                    <div className="stat">
                        <span className="stat-value">{summary.total}</span>
                        <span className="stat-label">Total Changes</span>
                    </div>
                    <div className="stat">
                        <span className="stat-value">{summary.modified}</span>
                        <span className="stat-label">Modified</span>
                    </div>
                    <div className="stat">
                        <span className="stat-value">{summary.added}</span>
                        <span className="stat-label">Added</span>
                    </div>
                    <div className="stat">
                        <span className="stat-value">{summary.deleted}</span>
                        <span className="stat-label">Deleted</span>
                    </div>
                </div>
            </div>

            {/* Hierarchical List */}
            <div className="file-list-container">
                {fileNames.length === 0 && (
                    <div className="empty-state-text" style={{ textAlign: 'center', padding: '2rem' }}>
                        No changes match the selected filter.
                    </div>
                )}

                {fileNames.map(fileName => {
                    const isFileExpanded = expandedFiles.has(fileName);
                    const sheets = hierarchy[fileName];
                    const sheetNames = Object.keys(sheets).sort();
                    const totalFileChanges = sheetNames.reduce((acc, s) => acc + sheets[s].length, 0);

                    return (
                        <div key={fileName} className="file-group">
                            <div
                                className={`file-header ${isFileExpanded ? 'expanded' : ''}`}
                                onClick={() => toggleFile(fileName)}
                            >
                                <span className="toggle-icon">{isFileExpanded ? '▼' : '▶'}</span>
                                <span className="file-icon">📄</span>
                                <span className="file-name">{fileName}</span>
                                <span className="badge badge-closed" style={{ marginLeft: 'auto' }}>
                                    {totalFileChanges} changes
                                </span>
                            </div>

                            {isFileExpanded && (
                                <div className="file-content">
                                    {sheetNames.map(sheetName => {
                                        const uniqueKey = `${fileName}|${sheetName}`;
                                        const isSheetExpanded = expandedSheets.has(uniqueKey);
                                        const sheetChanges = sheets[sheetName];
                                        const visibleCount = visibleCounts[uniqueKey] || PAGE_SIZE;
                                        const visibleChanges = sheetChanges.slice(0, visibleCount);
                                        const hasMore = visibleCount < sheetChanges.length;

                                        return (
                                            <div key={uniqueKey} className="sheet-group">
                                                <div
                                                    className="sheet-header"
                                                    onClick={() => toggleSheet(uniqueKey)}
                                                >
                                                    <span className="toggle-icon">{isSheetExpanded ? '▼' : '▶'}</span>
                                                    <span className="sheet-name">Sheet: {sheetName}</span>
                                                    <span className="count-label">({sheetChanges.length})</span>
                                                </div>

                                                {isSheetExpanded && (
                                                    <div className="sheet-content">
                                                        <div className="changes-table-container">
                                                            <table className="changes-table">
                                                                <thead>
                                                                    <tr>
                                                                        <th>Row</th>
                                                                        <th>Col</th>
                                                                        <th>Old Value</th>
                                                                        <th>New Value</th>
                                                                        <th>Description</th>
                                                                        <th>Type</th>
                                                                    </tr>
                                                                </thead>
                                                                <tbody>
                                                                    {visibleChanges.map((change, idx) => (
                                                                        <tr key={idx}>
                                                                            <td style={{ width: '60px' }}>{change.row}</td>
                                                                            <td style={{ width: '60px' }}>{change.col}</td>
                                                                            <td className="value-cell">
                                                                                {formatCellValue(change.oldValue, change.meta, true)}
                                                                            </td>
                                                                            <td className="value-cell">
                                                                                {formatCellValue(change.newValue, change.meta, false)}
                                                                            </td>
                                                                            <td style={{ fontSize: '0.85rem', color: '#555' }}>
                                                                                {generateChangeDescription(change)}
                                                                            </td>
                                                                            <td style={{ width: '100px' }}>
                                                                                <span
                                                                                    className="type-badge"
                                                                                    style={{ backgroundColor: getTypeColor(change.changeType) }}
                                                                                >
                                                                                    {change.changeType}
                                                                                </span>
                                                                            </td>
                                                                        </tr>
                                                                    ))}
                                                                </tbody>
                                                            </table>
                                                        </div>
                                                        {hasMore && (
                                                            <div className="show-more-container">
                                                                <button
                                                                    className="btn btn-secondary btn-sm"
                                                                    onClick={() => showMore(uniqueKey)}
                                                                    style={{ width: '100%', borderRadius: '0 0 8px 8px', marginTop: '-1px' }}
                                                                >
                                                                    Show More ({sheetChanges.length - visibleCount} remaining)
                                                                </button>
                                                            </div>
                                                        )}
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
