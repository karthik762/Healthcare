
import { useEffect, useState } from "react";

import {
    getDocuments,
    getDocument,
    createDocument,
    updateDocument,
    deleteDocument,
    uploadDocument,
    summarizeDocument
} from "./services/documentService";
import Auth from "./Auth";
import PatientIntake from "./PatientIntake";
import "./App.css";

function App() {
    const [currentUser, setCurrentUser] = useState(() => {
    const token = localStorage.getItem("token");
    const savedUser = localStorage.getItem("user");

    if (!token || !savedUser) {
        return null;
    }

    try {
        return JSON.parse(savedUser);
    } catch {
        localStorage.removeItem("token");
        localStorage.removeItem("user");
        return null;
    }
    });

    const handleLogin = (user) => {
        setCurrentUser(user);
    };

    const handleLogout = () => {
        localStorage.removeItem("token");
        localStorage.removeItem("user");
        setCurrentUser(null);
        setDocuments([]);
    };

    const [documents, setDocuments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [activePage, setActivePage] = useState("documents");

    const [showForm, setShowForm] = useState(false);
    const [editingId, setEditingId] = useState(null);
    const [viewingDocument, setViewingDocument] = useState(null);

    const [title, setTitle] = useState("");
    const [documentType, setDocumentType] = useState("Blood Report");
    const [content, setContent] = useState("");

    const [message, setMessage] = useState("");
    const [saving, setSaving] = useState(false);

    const [summary, setSummary] = useState("");
    const [summarizingId, setSummarizingId] = useState(null);

    // File upload states
    const [uploadTitle, setUploadTitle] = useState("");
    const [uploadType, setUploadType] = useState("Blood Report");
    const [selectedFile, setSelectedFile] = useState(null);

    // Load all documents
    const loadDocuments = async () => {
        try {
            const response = await getDocuments();
            setDocuments(response.data);
        } catch (error) {
            setMessage("Could not load documents. Check the backend.");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (currentUser) {
            loadDocuments();
        }
    }, [currentUser]);

    // Clear the manual document form
    const resetForm = () => {
        setTitle("");
        setDocumentType("Blood Report");
        setContent("");
        setEditingId(null);
        setShowForm(false);
    };

    // Open the form for a new document
    const handleAdd = () => {
        resetForm();
        setMessage("");
        setShowForm(true);
    };

    // View a complete document
    const handleView = async (id) => {
        setMessage("");

        try {
            const response = await getDocument(id);
            setViewingDocument(response.data);
        } catch (error) {
            setMessage("Could not load this document.");
        }
    };

    // Load existing values into the edit form
    const handleEdit = async (id) => {
        setMessage("");

        try {
            const response = await getDocument(id);
            const document = response.data;

            setTitle(document.title || "");
            setDocumentType(document.documentType || "Other");
            setContent(document.content || "");
            setEditingId(document.id);
            setShowForm(true);
            setViewingDocument(null);
        } catch (error) {
            setMessage("Could not load the document for editing.");
        }
    };

    // Add a new document or update an existing document
    const handleSubmit = async (event) => {
        event.preventDefault();
        setMessage("");

        if (!title.trim() || !content.trim()) {
            setMessage("Please enter a title and document content.");
            return;
        }

        setSaving(true);

        try {
            const documentData = {
                title: title.trim(),
                documentType,
                content: content.trim()
            };

            if (editingId !== null) {
                await updateDocument(editingId, documentData);
                setMessage("Document updated successfully.");
            } else {
                await createDocument(documentData);
                setMessage("Document added successfully.");
            }

            resetForm();
            await loadDocuments();
        } catch (error) {
            setMessage(
                editingId !== null
                    ? "Could not update the document."
                    : "Could not save the document."
            );
        } finally {
            setSaving(false);
        }
    };

    // Upload a PDF or TXT document
    const handleUpload = async (event) => {
        event.preventDefault();
        setMessage("");

        if (!uploadTitle.trim() || !selectedFile) {
            setMessage("Please enter a title and select a file.");
            return;
        }

        const fileName = selectedFile.name.toLowerCase();

        if (!fileName.endsWith(".pdf") && !fileName.endsWith(".txt")) {
            setMessage("Only PDF and TXT files are supported.");
            return;
        }

        setSaving(true);

        try {
            await uploadDocument(
                uploadTitle.trim(),
                uploadType,
                selectedFile
            );

            setMessage("Document uploaded successfully!");

            setUploadTitle("");
            setUploadType("Blood Report");
            setSelectedFile(null);

            event.currentTarget.reset();

            await loadDocuments();
        } catch (error) {
            const errorMessage = error.response?.data;

            setMessage(
                typeof errorMessage === "string"
                    ? errorMessage
                    : "Upload failed. Please check the backend."
            );
        } finally {
            setSaving(false);
        }
    };

    // Generate AI summary for a saved document
    const handleSummarize = async (id) => {
        setMessage("");
        setSummary("");
        setSummarizingId(id);

        try {
            const response = await summarizeDocument(id);
            setSummary(response.data.summary);
        } catch (error) {
            setMessage(
                error.response?.data?.error ||
                "Could not generate summary. Check that Ollama is running."
            );
        } finally {
            setSummarizingId(null);
        }
    };

    // Delete a document
    const handleDelete = async (id) => {
        const confirmed = window.confirm(
            "Are you sure you want to delete this document?"
        );

        if (!confirmed) {
            return;
        }

        setMessage("");

        try {
            await deleteDocument(id);

            if (viewingDocument?.id === id) {
                setViewingDocument(null);
            }

            if (editingId === id) {
                resetForm();
            }

            setMessage("Document deleted successfully.");
            await loadDocuments();
        } catch (error) {
            setMessage("Could not delete the document.");
        }
    };
    if (!currentUser) {
    return <Auth onLogin={handleLogin} />;
}

    return (
        <div className="app">
            {/* Navigation bar */}
            <header className="navbar">
            <h1>HealthSummarizer</h1>
            <span>Manage your health records</span>

            <div>
                <span>
                    {currentUser.name || currentUser.email}
                </span>

                <button onClick={handleLogout}>
                    Logout
                </button>
            </div>
            </header>

            {/* Navigation tabs */}
            <nav className="main-navigation">
                <button
                    className={
                        activePage === "documents"
                            ? "nav-button active"
                            : "nav-button"
                    }
                    onClick={() => setActivePage("documents")}
                >
                    Health Documents
                </button>

                <button
                    className={
                        activePage === "intake"
                            ? "nav-button active"
                            : "nav-button"
                    }
                    onClick={() => setActivePage("intake")}
                >
                    Patient Intake Assistant
                </button>
            </nav>

            <main className="container">
                {activePage === "intake" ? (
                    <PatientIntake />
                ) : (
                    <>
                        {/* Page heading */}
                        <div className="page-header">
                            <div>
                                <h2>My Health Documents</h2>
                                <p>
                                    Store and manage your health documents
                                    in one place.
                                </p>
                            </div>

                            <button
                                className="add-button"
                                onClick={handleAdd}
                            >
                                + Add Document
                            </button>
                        </div>

                        {/* Success and error messages */}
                        {message && (
                            <p className="message" role="status">
                                {message}
                            </p>
                        )}

                        {/* PDF and TXT upload form */}
                        <form
                            className="document-form"
                            onSubmit={handleUpload}
                        >
                            <h3>Upload Health Document</h3>

                            <p>
                                Upload a PDF or TXT file to save its
                                extracted text.
                            </p>

                            <label htmlFor="uploadTitle">
                                Document Title
                            </label>

                            <input
                                id="uploadTitle"
                                type="text"
                                value={uploadTitle}
                                onChange={(event) =>
                                    setUploadTitle(event.target.value)
                                }
                                placeholder="e.g. Thyroid Test Report"
                                required
                            />

                            <label htmlFor="uploadType">
                                Document Type
                            </label>

                            <select
                                id="uploadType"
                                value={uploadType}
                                onChange={(event) =>
                                    setUploadType(event.target.value)
                                }
                            >
                                <option>Blood Report</option>
                                <option>Prescription</option>
                                <option>Diagnostic Report</option>
                                <option>Medical History</option>
                                <option>Other</option>
                            </select>

                            <label htmlFor="uploadFile">
                                Choose PDF or TXT File
                            </label>

                            <input
                                id="uploadFile"
                                type="file"
                                accept=".pdf,.txt"
                                onChange={(event) =>
                                    setSelectedFile(
                                        event.target.files?.[0] || null
                                    )
                                }
                                required
                            />

                            {selectedFile && (
                                <p>Selected file: {selectedFile.name}</p>
                            )}

                            <div className="form-actions">
                                <button
                                    className="add-button"
                                    type="submit"
                                    disabled={saving}
                                >
                                    {saving
                                        ? "Uploading..."
                                        : "Upload Document"}
                                </button>
                            </div>
                        </form>

                        {/* Add and edit form */}
                        {showForm && (
                            <form
                                className="document-form"
                                onSubmit={handleSubmit}
                            >
                                <h3>
                                    {editingId !== null
                                        ? "Edit Health Document"
                                        : "Add a Health Document"}
                                </h3>

                                <label htmlFor="title">
                                    Document Title
                                </label>

                                <input
                                    id="title"
                                    type="text"
                                    value={title}
                                    onChange={(event) =>
                                        setTitle(event.target.value)
                                    }
                                    placeholder="e.g. Blood Test Report"
                                    required
                                />

                                <label htmlFor="documentType">
                                    Document Type
                                </label>

                                <select
                                    id="documentType"
                                    value={documentType}
                                    onChange={(event) =>
                                        setDocumentType(event.target.value)
                                    }
                                >
                                    <option>Blood Report</option>
                                    <option>Prescription</option>
                                    <option>Diagnostic Report</option>
                                    <option>Medical History</option>
                                    <option>Other</option>
                                </select>

                                <label htmlFor="content">
                                    Document Content
                                </label>

                                <textarea
                                    id="content"
                                    value={content}
                                    onChange={(event) =>
                                        setContent(event.target.value)
                                    }
                                    placeholder="Enter the health document text..."
                                    rows="7"
                                    required
                                />

                                <div className="form-actions">
                                    <button
                                        className="add-button"
                                        type="submit"
                                        disabled={saving}
                                    >
                                        {saving
                                            ? "Saving..."
                                            : editingId !== null
                                                ? "Update Document"
                                                : "Save Document"}
                                    </button>

                                    <button
                                        type="button"
                                        className="secondary-button"
                                        onClick={resetForm}
                                        disabled={saving}
                                    >
                                        Cancel
                                    </button>
                                </div>
                            </form>
                        )}

                        {/* Document list */}
                        {loading ? (
                            <p>Loading documents...</p>
                        ) : documents.length === 0 ? (
                            <div className="empty-state">
                                <h3>No documents found</h3>
                                <p>
                                    Add or upload your first health document
                                    to get started.
                                </p>
                            </div>
                        ) : (
                            <div className="document-list">
                                {documents.map((document) => (
                                    <div
                                        className="document-card"
                                        key={document.id}
                                    >
                                        <div className="document-details">
                                            <h3>{document.title}</h3>

                                            <span className="document-type">
                                                {document.documentType}
                                            </span>

                                            <p>
                                                {(document.content || "")
                                                    .substring(0, 150)}
                                                {(document.content || "").length > 150
                                                    ? "..."
                                                    : ""}
                                            </p>

                                            <small>
                                                ID: {document.id}
                                                {document.createdAt
                                                    ? ` • Created: ${new Date(
                                                        document.createdAt
                                                    ).toLocaleDateString()}`
                                                    : ""}
                                            </small>
                                        </div>

                                        <div className="card-actions">
                                            <button
                                                onClick={() =>
                                                    handleView(document.id)
                                                }
                                            >
                                                View
                                            </button>

                                            <button
                                                onClick={() =>
                                                    handleEdit(document.id)
                                                }
                                            >
                                                Edit
                                            </button>

                                            <button
                                                className="delete-button"
                                                onClick={() =>
                                                    handleDelete(document.id)
                                                }
                                            >
                                                Delete
                                            </button>

                                            <button
                                                onClick={() =>
                                                    handleSummarize(document.id)
                                                }
                                                disabled={summarizingId !== null}
                                            >
                                                {summarizingId === document.id
                                                    ? "Generating..."
                                                    : "Generate Summary"}
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}

                        {/* AI Summary modal */}
                        {summary && (
                            <div
                                className="modal-overlay"
                                onClick={() => setSummary("")}
                            >
                                <section
                                    className="view-modal"
                                    role="dialog"
                                    aria-modal="true"
                                    aria-labelledby="summary-title"
                                    onClick={(event) =>
                                        event.stopPropagation()
                                    }
                                >
                                    <h2 id="summary-title">
                                        AI Health Summary
                                    </h2>

                                    <p>
                                        Generated by Ollama from your saved
                                        document.
                                    </p>

                                    <div className="full-content">
                                        {summary}
                                    </div>

                                    <div className="form-actions">
                                        <button
                                            className="secondary-button"
                                            onClick={() => setSummary("")}
                                        >
                                            Close
                                        </button>
                                    </div>

                                    <small>
                                        For informational purposes only.
                                        Consult a qualified healthcare
                                        professional for medical interpretation.
                                    </small>
                                </section>
                            </div>
                        )}

                        {/* Full document view modal */}
                        {viewingDocument && (
                            <div
                                className="modal-overlay"
                                onClick={() => setViewingDocument(null)}
                            >
                                <section
                                    className="view-modal"
                                    role="dialog"
                                    aria-modal="true"
                                    aria-labelledby="view-title"
                                    onClick={(event) =>
                                        event.stopPropagation()
                                    }
                                >
                                    <h2 id="view-title">
                                        {viewingDocument.title}
                                    </h2>

                                    <p>
                                        <strong>Document ID:</strong>{" "}
                                        {viewingDocument.id}
                                    </p>

                                    <p>
                                        <strong>Type:</strong>{" "}
                                        {viewingDocument.documentType}
                                    </p>

                                    <p>
                                        <strong>Created:</strong>{" "}
                                        {viewingDocument.createdAt
                                            ? new Date(
                                                viewingDocument.createdAt
                                            ).toLocaleString()
                                            : "Not available"}
                                    </p>

                                    <h3>Full Document Content</h3>

                                    <div className="full-content">
                                        {viewingDocument.content}
                                    </div>

                                    <div className="form-actions">
                                        <button
                                            className="add-button"
                                            onClick={() =>
                                                handleEdit(viewingDocument.id)
                                            }
                                        >
                                            Edit Document
                                        </button>

                                        <button
                                            className="secondary-button"
                                            onClick={() =>
                                                setViewingDocument(null)
                                            }
                                        >
                                            Close
                                        </button>
                                    </div>
                                </section>
                            </div>
                        )}
                    </>
                )}
            </main>
        </div>
    );
}

export default App;
