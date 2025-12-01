import axios from 'axios';

const API_BASE = '/api';

export const api = {
    // Upload file with commit message and approvers
    uploadFile: async (file, commitMessage, approvers) => {
        const formData = new FormData();
        formData.append('file', file);
        if (commitMessage) {
            formData.append('commitMessage', commitMessage);
        }
        if (approvers && approvers.length > 0) {
            approvers.forEach(approver => {
                formData.append('approvers', approver);
            });
        }

        const response = await axios.post(`${API_BASE}/upload`, formData, {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
        });
        return response.data;
    },

    // Get collaborators
    getCollaborators: async () => {
        const response = await axios.get(`${API_BASE}/collaborators`);
        return response.data;
    },

    // Get commit history
    getCommits: async (searchQuery = '') => {
        const response = await axios.get(`${API_BASE}/commits`, {
            params: { search: searchQuery }
        });
        return response.data;
    },

    // Get commit details
    getCommitDetails: async (sha) => {
        const response = await axios.get(`${API_BASE}/commits/${sha}`);
        return response.data;
    },

    // Get pending approvals
    getPendingApprovals: async () => {
        const response = await axios.get(`${API_BASE}/approvals/pending`);
        return response.data;
    },

    // Get sent approvals
    getSentApprovals: async () => {
        const response = await axios.get(`${API_BASE}/approvals/sent`);
        return response.data;
    },

    // Approve PR
    approvePR: async (prNumber, comment) => {
        const response = await axios.post(`${API_BASE}/approvals/${prNumber}/approve`, {
            comment
        });
        return response.data;
    },

    // Reject PR
    rejectPR: async (prNumber, comment) => {
        const response = await axios.post(`${API_BASE}/approvals/${prNumber}/reject`, {
            comment
        });
        return response.data;
    },

    // Get PR changes
    getPRChanges: async (prNumber) => {
        const response = await axios.get(`${API_BASE}/approvals/${prNumber}/changes`);
        return response.data;
    },

    // Get commit changes
    getCommitChanges: async (sha) => {
        const response = await axios.get(`${API_BASE}/commits/${sha}/changes`);
        return response.data;
    }
};
