
import api from "../api";

const API_URL = "/documents";

export const getDocuments = () =>
    api.get(API_URL);

export const getDocument = (id) =>
    api.get(`${API_URL}/${id}`);

export const createDocument = (document) =>
    api.post(API_URL, document);

export const updateDocument = (id, document) =>
    api.put(`${API_URL}/${id}`, document);

export const deleteDocument = (id) =>
    api.delete(`${API_URL}/${id}`);

// Upload a PDF or TXT file
export const uploadDocument = (title, documentType, file) => {
    const formData = new FormData();

    formData.append("title", title);
    formData.append("documentType", documentType);
    formData.append("file", file);

    return api.post(`${API_URL}/upload-text`, formData);
};

// Generate an AI summary
export const summarizeDocument = (id) =>
    api.post(`${API_URL}/${id}/summarize`);
