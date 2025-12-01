import React, { useState, useMemo } from 'react';

export default function ChangePreview({ changes }) {
    const [selectedSheet, setSelectedSheet] = useState('All sheets');
    const [selectedFilter, setSelectedFilter] = useState('All');

    // Group changes by sheet
    const { sheets, summary } = useMemo(() => {
        const sheetMap = {};
        const summary = { total: 0, modified: 0, added: 0, deleted: 0 };

        changes.forEach(change => {
            const sheet = change.sheet || 'Unknown';
            if (!sheetMap[sheet]) {
                sheetMap[sheet] = [];
            }
            sheetMap[sheet].push(change);

            summary.total++;
            if (change.changeType === 'MODIFIED') summary.modified++;
            if (change.changeType === 'ADDED') summary.added++;
            if (change.changeType === 'DELETED') summary.deleted++;
        });

        return { sheets: sheetMap, summary };
    }, [changes]);

    const sheetNames = Object.keys(sheets);

    // Filter changes based on selected sheet and filter
    const filteredChanges = useMemo(() => {
        let filtered = changes;

        if (selectedSheet !== 'All sheets') {
            filtered = filtered.filter(c => c.sheet === selectedSheet);
        }

        if (selectedFilter !== 'All') {
            filtered = filtered.filter(c => c.changeType === selectedFilter.toUpperCase());
        }

        return filtered;
    }, [changes, selectedSheet, selectedFilter]);

    // Format cell value with styles
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

    if (!changes || changes.length === 0) {
        return (
            <div className="change-preview empty">
                <p>No changes detected</p>
            </div>
        );
    }

    return (
        <div className="change-preview">
            {/* Summary */}
            <div className="change-summary">
                <h3>Summary</h3>
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

            {/* Sheets selector */}
            {sheetNames.length > 1 && (
                <div className="sheet-selector">
                    <h4>Sheets</h4>
                    <div className="sheet-tabs">
                        <button
                            className={selectedSheet === 'All sheets' ? 'active' : ''}
                            onClick={() => setSelectedSheet('All sheets')}
                        >
                            All sheets
                        </button>
                        {sheetNames.map(sheet => (
                            <button
                                key={sheet}
                                className={selectedSheet === sheet ? 'active' : ''}
                                onClick={() => setSelectedSheet(sheet)}
                            >
                                {sheet}
                            </button>
                        ))}
                    </div>
                </div>
            )}

            {/* Filter */}
            <div className="change-filter">
                <button
                    className={selectedFilter === 'All' ? 'active' : ''}
                    onClick={() => setSelectedFilter('All')}
                >
                    All
                </button>
                <button
                    className={selectedFilter === 'Modified' ? 'active' : ''}
                    onClick={() => setSelectedFilter('Modified')}
                >
                    Modified
                </button>
                <button
                    className={selectedFilter === 'Added' ? 'active' : ''}
                    onClick={() => setSelectedFilter('Added')}
                >
                    Added
                </button>
                <button
                    className={selectedFilter === 'Deleted' ? 'active' : ''}
                    onClick={() => setSelectedFilter('Deleted')}
                >
                    Deleted
                </button>
            </div>

            {/* Changes table */}
            <div className="changes-table-container">
                <table className="changes-table">
                    <thead>
                        <tr>
                            <th>Sheet</th>
                            <th>Row</th>
                            <th>Col</th>
                            <th>Old Value</th>
                            <th>New Value</th>
                            <th>Type</th>
                        </tr>
                    </thead>
                    <tbody>
                        {filteredChanges.map((change, idx) => (
                            <tr key={idx}>
                                <td>{change.sheet}</td>
                                <td>{change.row}</td>
                                <td>{change.col}</td>
                                <td className="value-cell">
                                    {formatCellValue(change.oldValue, change.meta, true)}
                                </td>
                                <td className="value-cell">
                                    {formatCellValue(change.newValue, change.meta, false)}
                                </td>
                                <td>
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
        </div>
    );
}
