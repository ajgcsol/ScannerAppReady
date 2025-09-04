// Firebase configuration
const firebaseConfig = {
    apiKey: "AIzaSyCC9yAWbYIy56q2jLWkTyRG4pSPNay1KK0",
    authDomain: "scannerappfb.firebaseapp.com",
    projectId: "scannerappfb",
    storageBucket: "scannerappfb.firebasestorage.app",
    messagingSenderId: "339011672490",
    appId: "1:339011672490:android:0048a03844ebdb509273d3"
};

// Initialize Firebase
firebase.initializeApp(firebaseConfig);
const db = firebase.firestore();

// Global variables
let currentStudents = [];
let csvData = null;

// Tab management
function showTab(tabName) {
    // Hide all tab panes
    document.querySelectorAll('.tab-pane').forEach(pane => {
        pane.classList.remove('active');
    });
    
    // Remove active class from all tabs
    document.querySelectorAll('.tab').forEach(tab => {
        tab.classList.remove('active');
    });
    
    // Show selected tab pane
    document.getElementById(tabName + '-tab').classList.add('active');
    
    // Add active class to clicked tab
    event.target.classList.add('active');
    
    // Load data for specific tabs
    if (tabName === 'manage') {
        loadStudentData();
    } else if (tabName === 'analytics') {
        loadAnalytics();
    } else if (tabName === 'events') {
        loadEvents();
    }
}

// File upload handling
function handleDragOver(event) {
    event.preventDefault();
    event.currentTarget.classList.add('dragover');
}

function handleDrop(event) {
    event.preventDefault();
    event.currentTarget.classList.remove('dragover');
    
    const files = event.dataTransfer.files;
    if (files.length > 0) {
        handleFile(files[0]);
    }
}

function handleFileSelect(event) {
    const file = event.target.files[0];
    if (file) {
        handleFile(file);
    }
}

function handleFile(file) {
    if (!file.name.toLowerCase().endsWith('.csv')) {
        showMessage('Please select a CSV file.', 'error');
        return;
    }
    
    // Show file info
    document.getElementById('file-name').textContent = file.name;
    document.getElementById('file-size').textContent = formatFileSize(file.size);
    document.getElementById('file-preview').classList.remove('hidden');
    
    // Read and preview CSV
    const reader = new FileReader();
    reader.onload = function(e) {
        const csvText = e.target.result;
        parseAndPreviewCSV(csvText);
    };
    reader.readAsText(file);
}

function parseAndPreviewCSV(csvText) {
    const lines = csvText.split('\n');
    const headers = lines[0].split(',').map(h => h.trim());
    const rows = lines.slice(1).filter(line => line.trim().length > 0);
    
    // Store parsed data
    csvData = {
        headers: headers,
        rows: rows.map(row => {
            const values = row.split(',').map(v => v.trim().replace(/^"|"$/g, ''));
            const student = {};
            headers.forEach((header, index) => {
                student[header.toLowerCase().replace(/\s+/g, '')] = values[index] || '';
            });
            return student;
        })
    };
    
    // Create preview table
    let tableHTML = '<table class="preview-table"><thead><tr>';
    headers.forEach(header => {
        tableHTML += `<th>${header}</th>`;
    });
    tableHTML += '</tr></thead><tbody>';
    
    // Show first 5 rows as preview
    const previewRows = csvData.rows.slice(0, 5);
    previewRows.forEach(student => {
        tableHTML += '<tr>';
        headers.forEach(header => {
            const key = header.toLowerCase().replace(/\s+/g, '');
            tableHTML += `<td>${student[key] || ''}</td>`;
        });
        tableHTML += '</tr>';
    });
    
    if (csvData.rows.length > 5) {
        tableHTML += `<tr><td colspan="${headers.length}" style="text-align: center; font-style: italic; color: #718096;">... and ${csvData.rows.length - 5} more rows</td></tr>`;
    }
    
    tableHTML += '</tbody></table>';
    
    document.getElementById('csv-preview').innerHTML = `
        <h4 style="color: #2d3748; margin-bottom: 15px;">Preview (${csvData.rows.length} students total)</h4>
        ${tableHTML}
    `;
    
    showMessage(`Successfully parsed ${csvData.rows.length} student records.`, 'success');
}

function clearFile() {
    document.getElementById('csvFile').value = '';
    document.getElementById('file-preview').classList.add('hidden');
    csvData = null;
    clearMessage();
}

async function uploadData() {
    if (!csvData) {
        showMessage('Please select a CSV file first.', 'error');
        return;
    }
    
    const uploadBtn = document.getElementById('upload-btn');
    uploadBtn.innerHTML = '<span class="loading"></span>Uploading...';
    uploadBtn.disabled = true;
    
    try {
        const batch = db.batch();
        let successCount = 0;
        let skippedCount = 0;
        
        for (const student of csvData.rows) {
            // Validate required fields only (removed program and year from requirements)
            const studentId = student.studentid || student.id || '';
            const firstName = student.firstname || student.first_name || '';
            const lastName = student.lastname || student.last_name || '';
            const email = student.email || '';
            
            // Only require studentId, firstName, and lastName
            if (!studentId || !firstName || !lastName) {
                console.warn('Skipping incomplete record (missing required fields):', student);
                skippedCount++;
                continue;
            }
            
            const studentDoc = {
                studentId: studentId,
                firstName: firstName,
                lastName: lastName,
                email: email || '', // Email can be empty
                program: student.program || '', // Optional field
                year: student.year || '', // Optional field
                uploadedAt: firebase.firestore.FieldValue.serverTimestamp(),
                active: true
            };
            
            const docRef = db.collection('students').doc(studentId);
            batch.set(docRef, studentDoc, { merge: true });
            successCount++;
        }
        
        await batch.commit();
        
        let message = `Successfully uploaded ${successCount} student records to the database.`;
        if (skippedCount > 0) {
            message += ` (${skippedCount} records skipped due to missing required fields)`;
        }
        showMessage(message, 'success');
        
        // Update analytics
        await updateAnalytics();
        
        clearFile();
        
    } catch (error) {
        console.error('Upload error:', error);
        showMessage('Error uploading data: ' + error.message, 'error');
    } finally {
        uploadBtn.innerHTML = 'Upload to Database';
        uploadBtn.disabled = false;
    }
}

async function loadStudentData() {
    try {
        const studentsSnapshot = await db.collection('students')
            .orderBy('lastName')
            .limit(100)
            .get();
        
        currentStudents = studentsSnapshot.docs.map(doc => ({
            id: doc.id,
            ...doc.data()
        }));
        
        displayStudents(currentStudents);
        
    } catch (error) {
        console.error('Error loading students:', error);
        showMessage('Error loading student data: ' + error.message, 'error');
    }
}

function displayStudents(students) {
    const tbody = document.getElementById('students-tbody');
    
    if (students.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="5" style="text-align: center; padding: 40px; color: #718096;">
                    No students found. Upload CSV data to get started.
                </td>
            </tr>
        `;
        return;
    }
    
    tbody.innerHTML = students.map(student => `
        <tr>
            <td>${student.studentId || ''}</td>
            <td>${student.firstName || ''}</td>
            <td>${student.lastName || ''}</td>
            <td>${student.email || ''}</td>
            <td>
                <div style="display: flex; align-items: center; gap: 8px;">
                    <select id="student-action-${student.id}" class="btn btn-secondary" style="padding: 6px 10px; font-size: 0.9rem; cursor: pointer;">
                        <option value="">Select Action...</option>
                        <option value="view">View Details</option>
                        <option value="edit">Edit Student</option>
                        <option value="export">Export Data</option>
                        <option value="delete">Delete Student</option>
                    </select>
                    <button class="btn btn-primary" onclick="executeStudentAction('${student.id}', '${student.studentId}', '${(student.firstName || '').replace(/'/g, "\\'")}', '${(student.lastName || '').replace(/'/g, "\\'")}')" 
                            style="padding: 6px 12px; font-size: 0.9rem;">
                        Go
                    </button>
                </div>
            </td>
        </tr>
    `).join('');
}

function searchStudents() {
    const searchTerm = document.getElementById('search-input').value.toLowerCase();
    
    if (!searchTerm) {
        displayStudents(currentStudents);
        return;
    }
    
    const filteredStudents = currentStudents.filter(student => 
        (student.studentId || '').toLowerCase().includes(searchTerm) ||
        (student.firstName || '').toLowerCase().includes(searchTerm) ||
        (student.lastName || '').toLowerCase().includes(searchTerm) ||
        (student.email || '').toLowerCase().includes(searchTerm)
    );
    
    displayStudents(filteredStudents);
}

async function refreshStudentData() {
    showMessage('Refreshing student data...', 'info');
    await loadStudentData();
    clearMessage();
}

async function exportStudentData() {
    try {
        const csvContent = generateCSVFromStudents(currentStudents);
        downloadCSV(csvContent, 'students_export.csv');
        showMessage('Student data exported successfully.', 'success');
    } catch (error) {
        showMessage('Error exporting data: ' + error.message, 'error');
    }
}

async function loadAnalytics() {
    try {
        // Get total students
        const studentsSnapshot = await db.collection('students').get();
        document.getElementById('total-students').textContent = studentsSnapshot.size;
        
        // Get scans data
        const today = new Date();
        const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate());
        
        const scansSnapshot = await db.collection('scans')
            .where('timestamp', '>=', todayStart.getTime())
            .get();
        
        document.getElementById('scans-today').textContent = scansSnapshot.size;
        
        // Calculate unique students scanned
        const uniqueStudents = new Set(scansSnapshot.docs.map(doc => doc.data().code));
        document.getElementById('unique-scans').textContent = uniqueStudents.size;
        
        // Calculate success rate (assuming successful scans have student match)
        const successfulScans = scansSnapshot.docs.filter(doc => doc.data().verified === true);
        const successRate = scansSnapshot.size > 0 ? Math.round((successfulScans.length / scansSnapshot.size) * 100) : 0;
        document.getElementById('success-rate').textContent = successRate + '%';
        
        // Load recent activity
        loadRecentActivity();
        
    } catch (error) {
        console.error('Error loading analytics:', error);
    }
}

async function loadRecentActivity() {
    try {
        const recentScans = await db.collection('scans')
            .limit(50)
            .get();
        
        // Sort by timestamp and take the most recent 10
        const sortedScans = recentScans.docs
            .map(doc => ({ id: doc.id, ...doc.data() }))
            .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))
            .slice(0, 10);
        
        const activityHTML = sortedScans.map(scan => {
            const timestamp = new Date(scan.timestamp);
            return `
                <div style="display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid #e2e8f0;">
                    <div>
                        <strong>Student ID: ${scan.code}</strong>
                        <div style="color: #718096; font-size: 0.9rem;">${scan.verified ? '✅ Verified' : '❌ Not Found'}</div>
                    </div>
                    <div style="color: #718096; font-size: 0.9rem;">
                        ${timestamp.toLocaleString()}
                    </div>
                </div>
            `;
        }).join('');
        
        document.getElementById('recent-activity').innerHTML = activityHTML || 
            '<p style="color: #718096; text-align: center; padding: 20px;">No recent activity found.</p>';
        
    } catch (error) {
        console.error('Error loading recent activity:', error);
    }
}

async function updateAnalytics() {
    // Update system analytics in Firestore
    try {
        const studentsCount = await db.collection('students').get().then(snap => snap.size);
        
        await db.collection('analytics').doc('system').set({
            totalStudents: studentsCount,
            lastUpdated: firebase.firestore.FieldValue.serverTimestamp()
        }, { merge: true });
        
    } catch (error) {
        console.error('Error updating analytics:', error);
    }
}

// Utility functions
function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function showMessage(message, type) {
    const messageDiv = document.getElementById('status-message');
    messageDiv.textContent = message;
    messageDiv.className = `status-message status-${type}`;
    messageDiv.classList.remove('hidden');
    
    if (type === 'success' || type === 'info') {
        setTimeout(() => {
            clearMessage();
        }, 5000);
    }
}

function clearMessage() {
    const messageDiv = document.getElementById('status-message');
    messageDiv.classList.add('hidden');
}

function generateCSVFromStudents(students) {
    const headers = ['Student ID', 'First Name', 'Last Name', 'Email', 'Program', 'Year'];
    const csvRows = [headers.join(',')];
    
    students.forEach(student => {
        const row = [
            student.studentId || '',
            student.firstName || '',
            student.lastName || '',
            student.email || '',
            student.program || '',
            student.year || ''
        ];
        csvRows.push(row.map(field => `"${field}"`).join(','));
    });
    
    return csvRows.join('\n');
}

function downloadCSV(csvContent, filename) {
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    
    if (link.download !== undefined) {
        const url = URL.createObjectURL(blob);
        link.setAttribute('href', url);
        link.setAttribute('download', filename);
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    }
}

// Event Management Functions
async function loadEvents() {
    try {
        const eventsSnapshot = await db.collection('events')
            .get(); // Remove orderBy to avoid index issues
        
        const events = eventsSnapshot.docs.map(doc => ({
            id: doc.id,
            ...doc.data()
        }));
        
        // Sort events by creation date (most recent first) on client side
        events.sort((a, b) => {
            const dateA = a.createdAt ? a.createdAt.seconds : 0;
            const dateB = b.createdAt ? b.createdAt.seconds : 0;
            return dateB - dateA;
        });
        
        // Store original events for filtering
        window.originalEvents = events;
        
        // Populate the year filter dropdown
        populateYearFilter(events);
        
        // Apply initial filters (defaults to active events)
        applyEventFilters();
        
    } catch (error) {
        console.error('Error loading events:', error);
        document.getElementById('events-list').innerHTML = `
            <div style="background: #fed7d7; padding: 30px; text-align: center; border-radius: 15px; color: #742a2a;">
                Error loading events: ${error.message}
            </div>
        `;
    }
}

function displayEvents(events) {
    const eventsList = document.getElementById('events-list');
    
    if (events.length === 0) {
        eventsList.innerHTML = `
            <div style="background: #f7fafc; padding: 40px; text-align: center; border-radius: 15px; color: #718096;">
                No events found with current filters.
            </div>
        `;
        return;
    }
    
    // Store events globally for filtering
    window.allEvents = events;
    
    const eventsHTML = events.map(event => {
        const createdDate = event.createdAt ? new Date(event.createdAt.seconds * 1000).toLocaleDateString() : 'Unknown';
        const isActive = event.isActive ? '✅ Active' : '⏸️ Inactive';
        
        return `
            <div class="event-card" style="background: white; border: 2px solid #e2e8f0; border-radius: 15px; padding: 20px; margin-bottom: 15px;">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                    <div style="flex: 1;">
                        <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 8px;">
                            <h3 style="color: #2d3748; margin: 0;">Event #${event.eventNumber}: ${event.name}</h3>
                            <span style="background: ${event.isActive ? '#c6f6d5' : '#fed7d7'}; color: ${event.isActive ? '#22543d' : '#742a2a'}; padding: 4px 12px; border-radius: 20px; font-size: 0.85rem; font-weight: 600;">${isActive}</span>
                        </div>
                        ${event.description ? `<p style="color: #718096; margin: 5px 0; font-size: 0.95rem;">${event.description}</p>` : ''}
                        <div style="color: #4a5568; font-size: 0.85rem;">
                            <span>Created: ${createdDate}</span>
                        </div>
                    </div>
                    <div style="display: flex; align-items: center; gap: 10px;">
                        <select id="action-${event.id}" class="btn btn-secondary" style="padding: 8px 12px; font-size: 0.9rem; cursor: pointer;">
                            <option value="">Select Action...</option>
                            <option value="view">View Scans</option>
                            <option value="export-text">Export Text</option>
                            <option value="export-xlsx">Export XLSX</option>
                            <option value="export-errors">Export Errors</option>
                            <option value="edit-id">Edit Event ID</option>
                            <option value="toggle-status">${event.isActive ? 'Deactivate' : 'Activate'} Event</option>
                            <option value="delete">Delete Event</option>
                        </select>
                        <button class="btn btn-primary" onclick="executeEventAction('${event.id}', '${event.eventNumber}', '${event.name.replace(/'/g, "\\'")}', ${event.isActive})" 
                                style="padding: 8px 16px; font-size: 0.9rem;">
                            Go
                        </button>
                    </div>
                </div>
            </div>
        `;
    }).join('');
    
    eventsList.innerHTML = eventsHTML;
}

function executeEventAction(eventId, eventNumber, eventName, isActive) {
    const selectElement = document.getElementById(`action-${eventId}`);
    const action = selectElement.value;
    
    if (!action) {
        showMessage('Please select an action first.', 'error');
        return;
    }
    
    // Convert string parameters to proper types
    const eventNum = parseInt(eventNumber);
    const active = isActive === 'true' || isActive === true;
    
    switch(action) {
        case 'view':
            console.log('Executing view action for event:', eventId);
            viewDetailedScans(eventId);
            break;
        case 'export-text':
            exportEventText(eventId);
            break;
        case 'export-xlsx':
            exportEventXLSX(eventId);
            break;
        case 'export-errors':
            exportEventErrors(eventId);
            break;
        case 'edit-id':
            editEventNumber(eventId, eventNum);
            break;
        case 'toggle-status':
            toggleEventStatus(eventId, !active);
            break;
        case 'delete':
            deleteEvent(eventId, eventName);
            break;
        default:
            showMessage('Invalid action selected.', 'error');
    }
    
    // Reset the dropdown
    selectElement.value = '';
}

function applyEventFilters() {
    const searchTerm = document.getElementById('event-search').value.toLowerCase();
    const yearFilter = document.getElementById('event-year-filter').value;
    const statusFilter = document.getElementById('event-status-filter').value;
    
    if (!window.originalEvents || window.originalEvents.length === 0) {
        return;
    }
    
    let filteredEvents = window.originalEvents.filter(event => {
        // Text search filter
        const matchesSearch = !searchTerm || 
            event.name.toLowerCase().includes(searchTerm) ||
            event.eventNumber.toString().includes(searchTerm) ||
            (event.description && event.description.toLowerCase().includes(searchTerm));
        
        // Year filter
        const eventYear = event.createdAt ? new Date(event.createdAt.seconds * 1000).getFullYear().toString() : null;
        const matchesYear = yearFilter === 'all' || eventYear === yearFilter;
        
        // Status filter
        let matchesStatus = false;
        if (statusFilter === 'all') {
            matchesStatus = true;
        } else if (statusFilter === 'active') {
            matchesStatus = event.isActive === true;
        } else if (statusFilter === 'inactive') {
            matchesStatus = event.isActive === false;
        }
        
        return matchesSearch && matchesYear && matchesStatus;
    });
    
    displayEvents(filteredEvents);
}

function populateYearFilter(events) {
    const yearFilter = document.getElementById('event-year-filter');
    const years = new Set();
    
    events.forEach(event => {
        if (event.createdAt) {
            const year = new Date(event.createdAt.seconds * 1000).getFullYear();
            years.add(year);
        }
    });
    
    // Clear existing options except "All Years"
    yearFilter.innerHTML = '<option value="all">All Years</option>';
    
    // Add years in descending order
    Array.from(years).sort((a, b) => b - a).forEach(year => {
        const option = document.createElement('option');
        option.value = year.toString();
        option.textContent = year.toString();
        yearFilter.appendChild(option);
    });
}

function showNewEventForm() {
    document.getElementById('new-event-form').classList.remove('hidden');
}

function hideNewEventForm() {
    document.getElementById('new-event-form').classList.add('hidden');
    document.getElementById('event-number').value = '';
    document.getElementById('event-name').value = '';
    document.getElementById('event-description').value = '';
}

async function createNewEvent() {
    const eventNumber = parseInt(document.getElementById('event-number').value);
    const eventName = document.getElementById('event-name').value.trim();
    const eventDescription = document.getElementById('event-description').value.trim();
    
    if (!eventNumber || !eventName) {
        showMessage('Please provide both event number and name.', 'error');
        return;
    }
    
    const createBtn = document.getElementById('create-event-btn');
    createBtn.innerHTML = '<span class="loading"></span>Creating...';
    createBtn.disabled = true;
    
    try {
        // Check if event number already exists
        const existingEvent = await db.collection('events')
            .where('eventNumber', '==', eventNumber)
            .get();
        
        if (!existingEvent.empty) {
            showMessage(`Event number ${eventNumber} already exists. Please use a different number.`, 'error');
            createBtn.innerHTML = 'Create Event';
            createBtn.disabled = false;
            return;
        }
        
        const eventDoc = {
            eventNumber: eventNumber,
            name: eventName,
            description: eventDescription,
            createdAt: firebase.firestore.FieldValue.serverTimestamp(),
            isActive: true,
            exportFormat: 'TEXT_DELIMITED'
        };
        
        await db.collection('events').add(eventDoc);
        
        showMessage(`Event "${eventName}" created successfully!`, 'success');
        hideNewEventForm();
        loadEvents();
        
    } catch (error) {
        console.error('Error creating event:', error);
        showMessage('Error creating event: ' + error.message, 'error');
    } finally {
        createBtn.innerHTML = 'Create Event';
        createBtn.disabled = false;
    }
}

async function toggleEventStatus(eventId, newStatus) {
    try {
        await db.collection('events').doc(eventId).update({
            isActive: newStatus
        });
        
        showMessage(`Event ${newStatus ? 'activated' : 'deactivated'} successfully!`, 'success');
        loadEvents();
        
    } catch (error) {
        console.error('Error updating event status:', error);
        showMessage('Error updating event: ' + error.message, 'error');
    }
}

async function viewEventReport(eventId) {
    try {
        showMessage('Loading event report...', 'info');
        
        // Get event details
        const eventDoc = await db.collection('events').doc(eventId).get();
        if (!eventDoc.exists) {
            showMessage('Event not found.', 'error');
            return;
        }
        
        const event = eventDoc.data();
        
        // Get scans from BOTH structures using EVENT DOCUMENT ID (not event number)
        const [flatScansSnapshot, nestedScansSnapshot] = await Promise.all([
            // Flat structure (admin portal + new Flutter web app) - use eventId (document ID)
            db.collection('scans')
                .where('listId', '==', eventId)
                .get(),
            // Nested structure (Android app) - use EVENT DOCUMENT ID as listId
            db.collection('lists').doc(eventId)
                .collection('scans')
                .get()
        ]);
        
        // Combine scans from both sources, avoiding duplicates by ID
        const allScans = new Map();
        
        // Add flat structure scans
        flatScansSnapshot.docs.forEach(doc => {
            const scanData = doc.data();
            allScans.set(doc.id, scanData);
        });
        
        // Add nested structure scans (Android app data)
        nestedScansSnapshot.docs.forEach(doc => {
            const scanData = doc.data();
            // Convert Android app format to admin portal format
            const convertedScan = {
                code: scanData.code,
                timestamp: scanData.timestamp ? new Date(scanData.timestamp).getTime() : Date.now(),
                listId: eventId,
                deviceId: scanData.deviceId || '',
                verified: scanData.processed || false,
                symbology: scanData.symbology || '',
                studentId: scanData.studentId || '',
                firstName: '',
                lastName: '',
                email: ''
            };
            allScans.set(doc.id, convertedScan);
        });
        
        const scans = Array.from(allScans.values());
        
        // Generate report HTML
        const reportHTML = `
            <h3>Event Report: ${event.name} (#${event.eventNumber})</h3>
            <div style="margin: 20px 0;">
                <strong>Total Scans:</strong> ${scans.length}<br>
                <strong>Verified Students:</strong> ${scans.filter(s => s.verified).length}<br>
                <strong>Unverified Scans:</strong> ${scans.filter(s => !s.verified).length}<br>
                <strong>Sources:</strong> Flat: ${flatScansSnapshot.size}, Nested: ${nestedScansSnapshot.size}
            </div>
            <div style="max-height: 300px; overflow-y: auto;">
                <table class="preview-table">
                    <thead>
                        <tr>
                            <th>Student ID</th>
                            <th>Name</th>
                            <th>Time Scanned</th>
                            <th>Status</th>
                            <th>Source</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${scans.map(scan => `
                            <tr>
                                <td>${scan.code}</td>
                                <td>${scan.firstName || ''} ${scan.lastName || ''}</td>
                                <td>${new Date(scan.timestamp).toLocaleString()}</td>
                                <td>${scan.verified ? '✅ Verified' : '❌ Not Found'}</td>
                                <td>${flatScansSnapshot.docs.find(d => d.data().code === scan.code) ? 'Web' : 'Android'}</td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
        `;
        
        // You could open this in a modal or new window
        // For now, we'll just show a success message
        showMessage(`Report generated: ${scans.length} total scans for event "${event.name}" (${flatScansSnapshot.size} web + ${nestedScansSnapshot.size} android)`, 'success');
        
    } catch (error) {
        console.error('Error generating event report:', error);
        showMessage('Error generating report: ' + error.message, 'error');
    }
}

async function exportEventText(eventId) {
    try {
        showMessage('Preparing text export...', 'info');
        
        // Get event details
        const eventDoc = await db.collection('events').doc(eventId).get();
        if (!eventDoc.exists) {
            showMessage('Event not found.', 'error');
            return;
        }
        
        const event = eventDoc.data();
        
        // Get scans from BOTH structures
        const [flatScansSnapshot, nestedScansSnapshot] = await Promise.all([
            // Flat structure
            db.collection('scans')
                .where('listId', '==', eventId)
                .get(),
            // Nested structure (Android app)
            db.collection('lists').doc(eventId)
                .collection('scans')
                .get()
        ]);
        
        // Combine and deduplicate scans by student ID
        // Only include VALID scans (verified students with correct ID format)
        const uniqueStudentIds = new Set();
        let rejectedCount = 0;
        let invalidFormatCount = 0;
        let unverifiedCount = 0;
        
        // Regex pattern for valid student ID: 2 letters followed by 7 numbers
        const validIdPattern = /^[A-Za-z]{2}\d{7}$/;
        
        // Add flat structure scans - only verified ones with valid format
        flatScansSnapshot.docs.forEach(doc => {
            const scan = doc.data();
            const cleanId = scan.code.replace(/\s/g, '').toUpperCase();
            
            // Check format first
            if (!validIdPattern.test(cleanId)) {
                invalidFormatCount++;
                console.log(`Rejected invalid format: ${cleanId}`);
                rejectedCount++;
                return;
            }
            
            // Then check verification
            if (scan.verified !== true) {
                unverifiedCount++;
                console.log(`Rejected unverified: ${cleanId}`);
                rejectedCount++;
                return;
            }
            
            // Only add if both checks pass
            uniqueStudentIds.add(cleanId);
        });
        
        // Add nested structure scans - only verified ones with valid format
        nestedScansSnapshot.docs.forEach(doc => {
            const scan = doc.data();
            const cleanId = scan.code.replace(/\s/g, '').toUpperCase();
            
            // Check format first
            if (!validIdPattern.test(cleanId)) {
                invalidFormatCount++;
                console.log(`Rejected invalid format (Android): ${cleanId}`);
                rejectedCount++;
                return;
            }
            
            // Then check verification (Android uses processed field)
            if (scan.processed !== true) {
                unverifiedCount++;
                console.log(`Rejected unprocessed (Android): ${cleanId}`);
                rejectedCount++;
                return;
            }
            
            // Only add if both checks pass
            uniqueStudentIds.add(cleanId);
        });
        
        // Convert to sorted array and generate text content
        const sortedIds = Array.from(uniqueStudentIds).sort();
        const textContent = sortedIds
            .map(studentId => `${event.eventNumber} ${studentId}1`)
            .join('\n');
        
        const today = new Date();
        const dateString = `${(today.getMonth() + 1).toString().padStart(2, '0')}${today.getDate().toString().padStart(2, '0')}${today.getFullYear().toString().substr(2)}`;
        const filename = `Event_${event.eventNumber}_${dateString}.txt`;
        
        downloadTextFile(textContent, filename);
        
        const totalScans = flatScansSnapshot.size + nestedScansSnapshot.size;
        const duplicatesAndErrors = totalScans - sortedIds.length;
        showMessage(
            `Exported ${sortedIds.length} valid student IDs to ${filename}. ` +
            `Rejected ${rejectedCount} scans (${invalidFormatCount} invalid format, ${unverifiedCount} unverified). ` +
            `Total scans processed: ${totalScans}`, 
            'success'
        );
        
    } catch (error) {
        console.error('Error exporting text data:', error);
        showMessage('Error exporting data: ' + error.message, 'error');
    }
}

async function exportEventXLSX(eventId) {
    try {
        showMessage('Preparing XLSX export...', 'info');
        
        // Get event details
        const eventDoc = await db.collection('events').doc(eventId).get();
        if (!eventDoc.exists) {
            showMessage('Event not found.', 'error');
            return;
        }
        
        const event = eventDoc.data();
        
        // Get scans for this event
        const scansSnapshot = await db.collection('scans')
            .where('eventId', '==', eventId)
            .get();
        
        const scans = scansSnapshot.docs.map(doc => doc.data());
        
        // Create worksheet data
        const worksheetData = [
            ['Event', 'Student ID', 'First Name', 'Last Name', 'Email', 'Program', 'Year', 'Scanned At', 'Device', 'Status']
        ];
        
        scans.forEach(scan => {
            worksheetData.push([
                event.name,
                scan.code,
                scan.firstName || '',
                scan.lastName || '',
                scan.email || '',
                scan.program || '',
                scan.year || '',
                new Date(scan.timestamp).toLocaleString(),
                scan.deviceId,
                scan.verified ? 'Verified' : 'Unverified'
            ]);
        });
        
        // Create workbook and worksheet
        const wb = XLSX.utils.book_new();
        const ws = XLSX.utils.aoa_to_sheet(worksheetData);
        XLSX.utils.book_append_sheet(wb, ws, 'Event Report');
        
        // Download the file
        const today = new Date();
        const dateString = `${(today.getMonth() + 1).toString().padStart(2, '0')}${today.getDate().toString().padStart(2, '0')}${today.getFullYear().toString().substr(2)}`;
        const filename = `Event_${event.eventNumber}_Report_${dateString}.xlsx`;
        
        XLSX.writeFile(wb, filename);
        showMessage(`Exported ${scans.length} scans to ${filename}`, 'success');
        
    } catch (error) {
        console.error('Error exporting XLSX:', error);
        showMessage('Error exporting XLSX: ' + error.message, 'error');
    }
}

async function exportEventErrors(eventId) {
    try {
        showMessage('Preparing error export...', 'info');
        
        // Get event details
        const eventDoc = await db.collection('events').doc(eventId).get();
        if (!eventDoc.exists) {
            showMessage('Event not found.', 'error');
            return;
        }
        
        const event = eventDoc.data();
        
        // Get scans from BOTH structures
        const [flatScansSnapshot, nestedScansSnapshot] = await Promise.all([
            // Flat structure
            db.collection('scans')
                .where('listId', '==', eventId)
                .get(),
            // Nested structure (Android app)
            db.collection('lists').doc(eventId)
                .collection('scans')
                .get()
        ]);
        
        // Regex pattern for valid student ID: 2 letters followed by 7 numbers
        const validIdPattern = /^[A-Za-z]{2}\d{7}$/;
        
        // Collect all error scans
        const errorScans = [];
        
        // Process flat structure scans
        flatScansSnapshot.docs.forEach(doc => {
            const scan = doc.data();
            const cleanId = scan.code.replace(/\s/g, '').toUpperCase();
            let errorReason = null;
            
            if (!validIdPattern.test(cleanId)) {
                errorReason = 'Invalid ID Format (expected: 2 letters + 7 numbers)';
            } else if (scan.verified === false) {
                errorReason = 'Student Not Found in Database';
            }
            
            if (errorReason) {
                errorScans.push({
                    code: scan.code,
                    reason: errorReason,
                    source: 'Web Portal'
                });
            }
        });
        
        // Process nested structure scans
        nestedScansSnapshot.docs.forEach(doc => {
            const scan = doc.data();
            const cleanId = scan.code.replace(/\s/g, '').toUpperCase();
            let errorReason = null;
            
            if (!validIdPattern.test(cleanId)) {
                errorReason = 'Invalid ID Format (expected: 2 letters + 7 numbers)';
            } else if (scan.processed === false || scan.verified === false) {
                errorReason = 'Student Not Found in Database';
            }
            
            if (errorReason) {
                errorScans.push({
                    code: scan.code,
                    reason: errorReason,
                    source: 'Android App'
                });
            }
        });
        
        if (errorScans.length === 0) {
            showMessage('No errors found for this event.', 'info');
            return;
        }
        
        // Generate error content with more details
        const errorContent = errorScans.map(error => {
            return `${error.code} - ${error.reason} (Source: ${error.source})`;
        }).join('\n');
        
        const today = new Date();
        const dateString = `${(today.getMonth() + 1).toString().padStart(2, '0')}${today.getDate().toString().padStart(2, '0')}${today.getFullYear().toString().substr(2)}`;
        const filename = `Event_${event.eventNumber}_Errors_${dateString}.txt`;
        
        downloadTextFile(errorContent, filename);
        showMessage(`Exported ${errorScans.length} error records to ${filename}`, 'success');
        
    } catch (error) {
        console.error('Error exporting errors:', error);
        showMessage('Error exporting errors: ' + error.message, 'error');
    }
}

function downloadTextFile(content, filename) {
    const blob = new Blob([content], { type: 'text/plain;charset=utf-8;' });
    const link = document.createElement('a');
    
    if (link.download !== undefined) {
        const url = URL.createObjectURL(blob);
        link.setAttribute('href', url);
        link.setAttribute('download', filename);
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    }
}

function downloadTemplate() {
    // Create template CSV with headers only (no sample data)
    const templateContent = `StudentID,FirstName,LastName,Email`;
    
    const blob = new Blob([templateContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    
    if (link.download !== undefined) {
        const url = URL.createObjectURL(blob);
        link.setAttribute('href', url);
        link.setAttribute('download', 'student_upload_template.csv');
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    }
    
    showMessage('Template CSV downloaded. Fill in the required fields: StudentID, FirstName, LastName, and Email.', 'success');
}

async function clearAllStudentData() {
    try {
        // First, check if there's any data to clear
        const studentsSnapshot = await db.collection('students').get();
        
        if (studentsSnapshot.empty) {
            showMessage('No student data to clear.', 'info');
            return;
        }
        
        const studentCount = studentsSnapshot.size;
        
        // Show warning and backup prompt
        const backupConfirmed = confirm(
            `⚠️ WARNING: You are about to delete ALL ${studentCount} student records!\n\n` +
            `Would you like to create an archive backup first?\n\n` +
            `Click OK to create a backup before clearing.\n` +
            `Click Cancel to abort this operation.`
        );
        
        if (!backupConfirmed) {
            showMessage('Clear operation cancelled.', 'info');
            return;
        }
        
        const clearBtn = document.getElementById('clear-data-btn');
        clearBtn.innerHTML = '<span class="loading"></span>Creating Backup...';
        clearBtn.disabled = true;
        
        // Create automatic backup before clearing
        showMessage('Creating safety backup...', 'info');
        
        const backupId = `pre_clear_backup_${Date.now()}`;
        const backupData = {
            id: backupId,
            createdAt: firebase.firestore.FieldValue.serverTimestamp(),
            timestamp: Date.now(),
            description: `Safety backup before clearing all data (${studentCount} students)`,
            studentCount: studentCount,
            createdBy: 'System',
            type: 'pre_clear_backup'
        };
        
        // Save backup metadata
        await db.collection('archives').doc(backupId).set(backupData);
        
        // Backup all students
        const backupBatch = db.batch();
        studentsSnapshot.docs.forEach(doc => {
            const backupRef = db.collection('archives')
                .doc(backupId)
                .collection('students')
                .doc(doc.id);
            backupBatch.set(backupRef, {
                ...doc.data(),
                archivedAt: Date.now(),
                originalId: doc.id
            });
        });
        await backupBatch.commit();
        
        showMessage('Backup created successfully. Now clearing data...', 'info');
        
        // Now ask for final confirmation
        const finalConfirm = confirm(
            `✅ Backup has been created successfully!\n\n` +
            `Archive ID: ${backupId}\n` +
            `Students backed up: ${studentCount}\n\n` +
            `Are you ABSOLUTELY SURE you want to clear all student data?\n` +
            `This action cannot be undone (but you can restore from the backup).`
        );
        
        if (!finalConfirm) {
            clearBtn.innerHTML = 'Clear All Data';
            clearBtn.disabled = false;
            showMessage('Clear operation cancelled. Your backup has been saved.', 'info');
            loadArchiveHistory();
            loadArchivesList();
            return;
        }
        
        clearBtn.innerHTML = '<span class="loading"></span>Clearing Data...';
        
        // Clear all student records
        const clearBatch = db.batch();
        studentsSnapshot.docs.forEach(doc => {
            clearBatch.delete(doc.ref);
        });
        await clearBatch.commit();
        
        showMessage(
            `Successfully cleared ${studentCount} student records. ` +
            `A backup has been saved and can be restored from the Archive section.`, 
            'success'
        );
        
        // Update analytics
        await updateAnalytics();
        
        // Reload archive history to show the new backup
        loadArchiveHistory();
        loadArchivesList();
        
        // If on the manage tab, reload the (now empty) student list
        if (document.getElementById('manage-tab').classList.contains('active')) {
            loadStudentData();
        }
        
    } catch (error) {
        console.error('Error clearing student data:', error);
        showMessage('Error clearing data: ' + error.message, 'error');
    } finally {
        const clearBtn = document.getElementById('clear-data-btn');
        if (clearBtn) {
            clearBtn.innerHTML = 'Clear All Data';
            clearBtn.disabled = false;
        }
    }
}

async function deleteEvent(eventId, eventName) {
    // Show confirmation dialog
    const confirmed = confirm(
        `Are you sure you want to delete "${eventName}"?\n\n` +
        'This will permanently delete:\n' +
        '• The event record\n' +
        '• All associated attendance data\n' +
        '• All scan records for this event\n\n' +
        'This action cannot be undone!'
    );
    
    if (!confirmed) {
        return;
    }
    
    try {
        showMessage('Deleting event and associated data...', 'info');
        
        // Start a batch operation to delete event and related data
        const batch = db.batch();
        
        // 1. Delete the event document
        const eventRef = db.collection('events').doc(eventId);
        batch.delete(eventRef);
        
        // 2. Delete all scans associated with this event
        const scansSnapshot = await db.collection('scans')
            .where('listId', '==', eventId)
            .get();
        
        scansSnapshot.docs.forEach(doc => {
            batch.delete(doc.ref);
        });
        
        // 3. Delete all attendees associated with this event
        const attendeesSnapshot = await db.collection('attendees')
            .where('eventId', '==', eventId)
            .get();
        
        attendeesSnapshot.docs.forEach(doc => {
            batch.delete(doc.ref);
        });
        
        // Execute all deletions
        await batch.commit();
        
        showMessage(
            `Successfully deleted event "${eventName}" and all associated data ` +
            `(${scansSnapshot.size} scans, ${attendeesSnapshot.size} attendees).`, 
            'success'
        );
        
        // Reload the events list
        loadEvents();
        
    } catch (error) {
        console.error('Error deleting event:', error);
        showMessage('Error deleting event: ' + error.message, 'error');
    }
}

// New Event Management Functions
async function viewDetailedScans(eventId) {
    try {
        console.log('viewDetailedScans called with eventId:', eventId);
        showMessage('Loading scan details...', 'info');
        
        // Get event details
        const eventDoc = await db.collection('events').doc(eventId).get();
        if (!eventDoc.exists) {
            showMessage('Event not found.', 'error');
            return;
        }
        
        const event = eventDoc.data();
        
        // Get scans from BOTH structures
        const [flatScansSnapshot, nestedScansSnapshot] = await Promise.all([
            // Flat structure - remove orderBy to avoid index requirement, we'll sort in memory
            db.collection('scans')
                .where('listId', '==', eventId)
                .get(),
            // Nested structure (Android app) - also remove orderBy
            db.collection('lists').doc(eventId)
                .collection('scans')
                .get()
        ]);
        
        // Combine all scans
        const allScans = [];
        
        // Add flat structure scans with source indicator
        flatScansSnapshot.docs.forEach(doc => {
            const scanData = doc.data();
            allScans.push({
                ...scanData,
                id: doc.id,
                source: 'Web Portal'
            });
        });
        
        // Add nested structure scans
        nestedScansSnapshot.docs.forEach(doc => {
            const scanData = doc.data();
            allScans.push({
                code: scanData.code,
                timestamp: scanData.timestamp ? new Date(scanData.timestamp).getTime() : Date.now(),
                listId: eventId,
                deviceId: scanData.deviceId || '',
                verified: scanData.processed || false,
                symbology: scanData.symbology || '',
                studentId: scanData.studentId || scanData.code,
                id: doc.id,
                source: 'Android App'
            });
        });
        
        // Sort by timestamp (most recent first)
        allScans.sort((a, b) => {
            const timeA = a.timestamp || 0;
            const timeB = b.timestamp || 0;
            return timeB - timeA;
        });
        
        // Look up student details for each scan
        const scansWithDetails = await Promise.all(allScans.map(async (scan) => {
            try {
                const studentDoc = await db.collection('students').doc(scan.code).get();
                if (studentDoc.exists) {
                    const student = studentDoc.data();
                    return {
                        ...scan,
                        firstName: student.firstName || '',
                        lastName: student.lastName || '',
                        email: student.email || '',
                        program: student.program || '',
                        year: student.year || ''
                    };
                }
            } catch (err) {
                // Student not found
            }
            return scan;
        }));
        
        // Create detailed view modal
        console.log('Creating modal for event:', event.name, 'with', allScans.length, 'scans');
        
        const modalHTML = `
            <div style="position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 1000; display: flex; align-items: center; justify-content: center;">
                <div style="background: white; border-radius: 20px; padding: 30px; width: 90%; max-width: 1200px; max-height: 85vh; display: flex; flex-direction: column; box-shadow: 0 20px 60px rgba(0,0,0,0.3);">
                    <div style="margin-bottom: 20px;">
                        <h2 style="color: #2d3748; margin-bottom: 10px;">Event Scans: ${event.name.replace(/"/g, '&quot;')} (#${event.eventNumber})</h2>
                        <div style="display: flex; gap: 20px; color: #4a5568;">
                            <span><strong>Total Scans:</strong> ${allScans.length}</span>
                            <span><strong>Unique Students:</strong> ${new Set(allScans.map(s => s.code)).size}</span>
                            <span><strong>Web Scans:</strong> ${flatScansSnapshot.size}</span>
                            <span><strong>Android Scans:</strong> ${nestedScansSnapshot.size}</span>
                        </div>
                    </div>
                    
                    <div style="margin-bottom: 15px;">
                        <input type="text" id="scan-search" placeholder="Search by Student ID, Name, or Email..." 
                               onkeyup="filterScans()" 
                               style="width: 100%; padding: 10px; border: 2px solid #e2e8f0; border-radius: 8px;">
                    </div>
                    
                    <div style="flex: 1; overflow-y: auto; border: 1px solid #e2e8f0; border-radius: 10px;">
                        <table class="preview-table" style="margin: 0;">
                            <thead style="position: sticky; top: 0; background: white;">
                                <tr>
                                    <th style="padding: 12px;">#</th>
                                    <th style="padding: 12px;">Student ID</th>
                                    <th style="padding: 12px;">Name</th>
                                    <th style="padding: 12px;">Email</th>
                                    <th style="padding: 12px;">Program</th>
                                    <th style="padding: 12px;">Year</th>
                                    <th style="padding: 12px;">Scan Time</th>
                                    <th style="padding: 12px;">Source</th>
                                    <th style="padding: 12px;">Status</th>
                                </tr>
                            </thead>
                            <tbody id="scans-tbody">
                                ${scansWithDetails.map((scan, index) => `
                                    <tr class="scan-row">
                                        <td style="padding: 12px;">${index + 1}</td>
                                        <td style="padding: 12px; font-weight: 600;">${scan.code}</td>
                                        <td style="padding: 12px;">${scan.firstName || ''} ${scan.lastName || ''}</td>
                                        <td style="padding: 12px;">${scan.email || '-'}</td>
                                        <td style="padding: 12px;">${scan.program || '-'}</td>
                                        <td style="padding: 12px;">${scan.year || '-'}</td>
                                        <td style="padding: 12px;">${new Date(scan.timestamp).toLocaleString()}</td>
                                        <td style="padding: 12px;">
                                            <span style="background: ${scan.source === 'Web Portal' ? '#bee3f8' : '#c6f6d5'}; 
                                                       color: ${scan.source === 'Web Portal' ? '#2a4365' : '#22543d'}; 
                                                       padding: 2px 8px; border-radius: 12px; font-size: 0.85rem;">
                                                ${scan.source}
                                            </span>
                                        </td>
                                        <td style="padding: 12px;">
                                            ${scan.verified || (scan.firstName && scan.lastName) ? 
                                              '<span style="color: #22543d;">✅ Verified</span>' : 
                                              '<span style="color: #e53e3e;">❌ Not Found</span>'}
                                        </td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                    
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 20px;">
                        <div>
                            <button class="btn btn-secondary" onclick="exportDetailedReport('${eventId}')">Export Detailed Report</button>
                        </div>
                        <button class="btn btn-primary" onclick="closeScanDetails()">Close</button>
                    </div>
                </div>
            </div>
        `;
        
        // Create and append modal
        console.log('Creating modal div and appending to body');
        const modalDiv = document.createElement('div');
        modalDiv.id = 'scan-details-modal';
        modalDiv.innerHTML = modalHTML;
        document.body.appendChild(modalDiv);
        
        // Store scans data for filtering
        window.currentScansData = scansWithDetails;
        
        console.log('Modal created and added to DOM');
        clearMessage();
        
    } catch (error) {
        console.error('Error loading scan details:', error);
        showMessage('Error loading scan details: ' + error.message, 'error');
    }
}

function filterScans() {
    const searchTerm = document.getElementById('scan-search').value.toLowerCase();
    const rows = document.querySelectorAll('#scans-tbody .scan-row');
    
    rows.forEach(row => {
        const text = row.textContent.toLowerCase();
        row.style.display = text.includes(searchTerm) ? '' : 'none';
    });
}

function closeScanDetails() {
    const modal = document.getElementById('scan-details-modal');
    if (modal) {
        modal.remove();
    }
    window.currentScansData = null;
}

async function exportDetailedReport(eventId) {
    if (!window.currentScansData) {
        showMessage('No scan data available to export.', 'error');
        return;
    }
    
    try {
        // Create worksheet data
        const worksheetData = [
            ['#', 'Student ID', 'First Name', 'Last Name', 'Email', 'Program', 'Year', 'Scan Time', 'Source', 'Status']
        ];
        
        window.currentScansData.forEach((scan, index) => {
            worksheetData.push([
                index + 1,
                scan.code,
                scan.firstName || '',
                scan.lastName || '',
                scan.email || '',
                scan.program || '',
                scan.year || '',
                new Date(scan.timestamp).toLocaleString(),
                scan.source,
                (scan.verified || (scan.firstName && scan.lastName)) ? 'Verified' : 'Not Found'
            ]);
        });
        
        // Create workbook and worksheet
        const wb = XLSX.utils.book_new();
        const ws = XLSX.utils.aoa_to_sheet(worksheetData);
        XLSX.utils.book_append_sheet(wb, ws, 'Detailed Scans');
        
        // Download the file
        const today = new Date();
        const dateString = `${today.getFullYear()}${(today.getMonth() + 1).toString().padStart(2, '0')}${today.getDate().toString().padStart(2, '0')}`;
        const filename = `Event_Detailed_Report_${dateString}.xlsx`;
        
        XLSX.writeFile(wb, filename);
        showMessage(`Exported detailed report with ${window.currentScansData.length} scans.`, 'success');
        
    } catch (error) {
        console.error('Error exporting detailed report:', error);
        showMessage('Error exporting report: ' + error.message, 'error');
    }
}

async function editEventNumber(eventId, currentNumber) {
    const newNumber = prompt(`Enter new event ID number (current: ${currentNumber}):`, currentNumber);
    
    if (newNumber === null || newNumber === '') {
        return;
    }
    
    const newNumberInt = parseInt(newNumber);
    if (isNaN(newNumberInt)) {
        showMessage('Please enter a valid number.', 'error');
        return;
    }
    
    if (newNumberInt === currentNumber) {
        return;
    }
    
    try {
        // Check if new number already exists
        const existingEvent = await db.collection('events')
            .where('eventNumber', '==', newNumberInt)
            .get();
        
        if (!existingEvent.empty) {
            showMessage(`Event number ${newNumberInt} is already in use. Please choose a different number.`, 'error');
            return;
        }
        
        // Update the event number
        await db.collection('events').doc(eventId).update({
            eventNumber: newNumberInt
        });
        
        showMessage(`Event ID updated from ${currentNumber} to ${newNumberInt}.`, 'success');
        loadEvents();
        
    } catch (error) {
        console.error('Error updating event number:', error);
        showMessage('Error updating event ID: ' + error.message, 'error');
    }
}

// Manual Student Entry Functions
function showAddStudentForm() {
    document.getElementById('add-student-form').classList.remove('hidden');
    document.getElementById('new-student-id').focus();
}

function hideAddStudentForm() {
    document.getElementById('add-student-form').classList.add('hidden');
    clearAddStudentForm();
}

function clearAddStudentForm() {
    document.getElementById('new-student-id').value = '';
    document.getElementById('new-student-firstname').value = '';
    document.getElementById('new-student-lastname').value = '';
    document.getElementById('new-student-email').value = '';
}

async function addNewStudent() {
    const studentId = document.getElementById('new-student-id').value.trim();
    const firstName = document.getElementById('new-student-firstname').value.trim();
    const lastName = document.getElementById('new-student-lastname').value.trim();
    const email = document.getElementById('new-student-email').value.trim();
    
    // Validate required fields
    if (!studentId || !firstName || !lastName) {
        showMessage('Please fill in all required fields (Student ID, First Name, Last Name).', 'error');
        return;
    }
    
    // Validate student ID format (should be 9 digits)
    if (!/^\d{9}$/.test(studentId)) {
        showMessage('Student ID must be exactly 9 digits.', 'error');
        return;
    }
    
    const addBtn = document.getElementById('add-student-btn');
    addBtn.innerHTML = '<span class="loading"></span>Adding...';
    addBtn.disabled = true;
    
    try {
        // Check if student ID already exists
        const existingStudent = await db.collection('students').doc(studentId).get();
        
        if (existingStudent.exists) {
            showMessage(`Student ID ${studentId} already exists in the database.`, 'error');
            addBtn.innerHTML = 'Add Student';
            addBtn.disabled = false;
            return;
        }
        
        // Create new student document
        const studentDoc = {
            studentId: studentId,
            firstName: firstName,
            lastName: lastName,
            email: email || '',
            program: '',
            year: '',
            uploadedAt: firebase.firestore.FieldValue.serverTimestamp(),
            addedManually: true,
            active: true
        };
        
        await db.collection('students').doc(studentId).set(studentDoc);
        
        showMessage(`Successfully added ${firstName} ${lastName} (ID: ${studentId}) to the database.`, 'success');
        
        // Clear form and hide it
        hideAddStudentForm();
        
        // Update analytics
        await updateAnalytics();
        
        // Reload student data if we're on the manage tab
        if (document.getElementById('manage-tab').classList.contains('active')) {
            loadStudentData();
        }
        
    } catch (error) {
        console.error('Error adding student:', error);
        showMessage('Error adding student: ' + error.message, 'error');
    } finally {
        addBtn.innerHTML = 'Add Student';
        addBtn.disabled = false;
    }
}

// Student Action Functions
function executeStudentAction(studentId, studentNumber, firstName, lastName) {
    const selectElement = document.getElementById(`student-action-${studentId}`);
    const action = selectElement.value;
    
    if (!action) {
        showMessage('Please select an action first.', 'error');
        return;
    }
    
    switch(action) {
        case 'view':
            viewStudentDetails(studentId);
            break;
        case 'edit':
            editStudent(studentId);
            break;
        case 'export':
            exportStudentData(studentId, studentNumber, firstName, lastName);
            break;
        case 'delete':
            deleteStudent(studentId, studentNumber, firstName, lastName);
            break;
        default:
            showMessage('Invalid action selected.', 'error');
    }
    
    // Reset the dropdown
    selectElement.value = '';
}

async function viewStudentDetails(studentId) {
    try {
        const studentDoc = await db.collection('students').doc(studentId).get();
        
        if (!studentDoc.exists) {
            showMessage('Student not found.', 'error');
            return;
        }
        
        const student = studentDoc.data();
        
        const content = `
            <div class="info-section">
                <h3>Student Information</h3>
                <p><strong>Student ID:</strong> ${student.studentId || 'N/A'}</p>
                <p><strong>Name:</strong> ${student.firstName || ''} ${student.lastName || ''}</p>
                <p><strong>Email:</strong> ${student.email || 'N/A'}</p>
                <p><strong>Program:</strong> ${student.program || 'Not specified'}</p>
                <p><strong>Year:</strong> ${student.year || 'Not specified'}</p>
                <p><strong>Status:</strong> ${student.active ? 'Active' : 'Inactive'}</p>
                <p><strong>Added:</strong> ${student.uploadedAt ? new Date(student.uploadedAt.seconds * 1000).toLocaleString() : 'Unknown'}</p>
                <p><strong>Added Method:</strong> ${student.addedManually ? 'Manual Entry' : 'CSV Upload'}</p>
            </div>
            
            <div class="info-section">
                <h3>Scan History</h3>
                <p style="color: #6b7280;">Loading scan history...</p>
            </div>
        `;
        
        showModal(`Student Details: ${student.firstName} ${student.lastName}`, content);
        
        // Load scan history
        loadStudentScanHistory(studentId);
        
    } catch (error) {
        console.error('Error viewing student details:', error);
        showMessage('Error loading student details: ' + error.message, 'error');
    }
}

async function loadStudentScanHistory(studentId) {
    try {
        const scansSnapshot = await db.collection('scans')
            .where('code', '==', studentId)
            .limit(20)
            .get();
        
        const modalBody = document.getElementById('modal-body');
        const scanSection = modalBody.querySelector('.info-section:last-child');
        
        if (scansSnapshot.empty) {
            scanSection.innerHTML = '<h3>Scan History</h3><p style="color: #6b7280;">No scan history found for this student.</p>';
        } else {
            let scanHTML = '<h3>Scan History</h3><div style="max-height: 200px; overflow-y: auto;">';
            
            scansSnapshot.docs.forEach(doc => {
                const scan = doc.data();
                const timestamp = new Date(scan.timestamp);
                scanHTML += `
                    <div style="padding: 8px; border-bottom: 1px solid #e5e7eb;">
                        <strong>Event:</strong> ${scan.listId || 'Unknown'}<br>
                        <strong>Time:</strong> ${timestamp.toLocaleString()}<br>
                        <strong>Status:</strong> ${scan.verified ? '✅ Verified' : '❌ Not Verified'}
                    </div>
                `;
            });
            
            scanHTML += '</div>';
            scanSection.innerHTML = scanHTML;
        }
        
    } catch (error) {
        console.error('Error loading scan history:', error);
    }
}

function editStudent(studentId) {
    showMessage('Edit functionality coming soon!', 'info');
}

function exportStudentData(studentId, studentNumber, firstName, lastName) {
    const csvContent = `StudentID,FirstName,LastName,Email\n${studentNumber},${firstName},${lastName},`;
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    
    if (link.download !== undefined) {
        const url = URL.createObjectURL(blob);
        link.setAttribute('href', url);
        link.setAttribute('download', `student_${studentNumber}.csv`);
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    }
    
    showMessage(`Exported data for ${firstName} ${lastName}`, 'success');
}

async function deleteStudent(studentId, studentNumber, firstName, lastName) {
    const confirmed = confirm(
        `Are you sure you want to delete this student?\n\n` +
        `Student ID: ${studentNumber}\n` +
        `Name: ${firstName} ${lastName}\n\n` +
        `This action cannot be undone!`
    );
    
    if (!confirmed) {
        return;
    }
    
    try {
        await db.collection('students').doc(studentId).delete();
        
        showMessage(`Successfully deleted student ${firstName} ${lastName} (ID: ${studentNumber})`, 'success');
        
        // Reload student data
        loadStudentData();
        
    } catch (error) {
        console.error('Error deleting student:', error);
        showMessage('Error deleting student: ' + error.message, 'error');
    }
}

// Archive Management Functions
async function createArchive() {
    const description = document.getElementById('archive-description').value.trim();
    const createBtn = document.getElementById('create-archive-btn');
    
    createBtn.innerHTML = '<span class="loading"></span>Creating Archive...';
    createBtn.disabled = true;
    
    try {
        // Get all current students
        const studentsSnapshot = await db.collection('students').get();
        
        if (studentsSnapshot.empty) {
            showMessage('No student data to archive.', 'error');
            createBtn.innerHTML = 'Create Archive Backup';
            createBtn.disabled = false;
            return;
        }
        
        // Create archive metadata
        const archiveId = `archive_${Date.now()}`;
        const archiveData = {
            id: archiveId,
            createdAt: firebase.firestore.FieldValue.serverTimestamp(),
            timestamp: Date.now(),
            description: description || 'Manual backup',
            studentCount: studentsSnapshot.size,
            createdBy: 'Admin Portal',
            type: 'full_backup'
        };
        
        // Save archive metadata
        await db.collection('archives').doc(archiveId).set(archiveData);
        
        // Create batch for archiving students
        const batch = db.batch();
        let archivedCount = 0;
        
        studentsSnapshot.docs.forEach(doc => {
            const studentData = doc.data();
            const archiveStudentRef = db.collection('archives')
                .doc(archiveId)
                .collection('students')
                .doc(doc.id);
            
            batch.set(archiveStudentRef, {
                ...studentData,
                archivedAt: Date.now(),
                originalId: doc.id
            });
            archivedCount++;
        });
        
        await batch.commit();
        
        showMessage(`Successfully archived ${archivedCount} student records.`, 'success');
        
        // Clear the description field
        document.getElementById('archive-description').value = '';
        
        // Reload archive history
        loadArchiveHistory();
        loadArchivesList();
        
    } catch (error) {
        console.error('Error creating archive:', error);
        showMessage('Error creating archive: ' + error.message, 'error');
    } finally {
        createBtn.innerHTML = 'Create Archive Backup';
        createBtn.disabled = false;
    }
}

async function loadArchivesList() {
    try {
        const archivesSnapshot = await db.collection('archives')
            .orderBy('timestamp', 'desc')
            .get();
        
        const selectElement = document.getElementById('archive-select');
        
        if (archivesSnapshot.empty) {
            selectElement.innerHTML = '<option value="">No archives available</option>';
            return;
        }
        
        selectElement.innerHTML = '<option value="">Select an archive...</option>';
        
        archivesSnapshot.docs.forEach(doc => {
            const archive = doc.data();
            const date = new Date(archive.timestamp);
            const dateString = date.toLocaleString();
            const option = document.createElement('option');
            option.value = doc.id;
            option.textContent = `${dateString} - ${archive.description} (${archive.studentCount} students)`;
            selectElement.appendChild(option);
        });
        
    } catch (error) {
        console.error('Error loading archives:', error);
    }
}

async function loadArchiveHistory() {
    try {
        const archivesSnapshot = await db.collection('archives')
            .orderBy('timestamp', 'desc')
            .limit(10)
            .get();
        
        const historyDiv = document.getElementById('archive-history');
        
        if (archivesSnapshot.empty) {
            historyDiv.innerHTML = '<p style="color: #718096; text-align: center;">No archives created yet.</p>';
            return;
        }
        
        let historyHTML = '<div style="overflow-x: auto;"><table class="preview-table">';
        historyHTML += `
            <thead>
                <tr>
                    <th>Date & Time</th>
                    <th>Description</th>
                    <th>Students</th>
                    <th>Created By</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
        `;
        
        archivesSnapshot.docs.forEach(doc => {
            const archive = doc.data();
            const date = new Date(archive.timestamp);
            const dateString = date.toLocaleString();
            
            historyHTML += `
                <tr>
                    <td>${dateString}</td>
                    <td>${archive.description}</td>
                    <td>${archive.studentCount}</td>
                    <td>${archive.createdBy}</td>
                    <td>
                        <button class="btn btn-secondary" style="padding: 5px 10px; font-size: 0.9rem; margin-right: 5px;" 
                                onclick="viewArchiveById('${doc.id}')">View</button>
                        <button class="btn btn-primary" style="padding: 5px 10px; font-size: 0.9rem; margin-right: 5px; background: #f6ad55;" 
                                onclick="restoreArchiveById('${doc.id}')">Restore</button>
                        <button class="btn" style="padding: 5px 10px; font-size: 0.9rem; background: #fed7d7; color: #742a2a;" 
                                onclick="deleteArchive('${doc.id}')">Delete</button>
                    </td>
                </tr>
            `;
        });
        
        historyHTML += '</tbody></table></div>';
        historyDiv.innerHTML = historyHTML;
        
    } catch (error) {
        console.error('Error loading archive history:', error);
        document.getElementById('archive-history').innerHTML = 
            '<p style="color: #e53e3e; text-align: center;">Error loading archive history.</p>';
    }
}

async function viewArchive() {
    const archiveId = document.getElementById('archive-select').value;
    if (!archiveId) {
        showMessage('Please select an archive to view.', 'error');
        return;
    }
    
    await viewArchiveById(archiveId);
}

async function viewArchiveById(archiveId) {
    const viewBtn = document.getElementById('view-archive-btn');
    if (viewBtn) {
        viewBtn.innerHTML = '<span class="loading"></span>Loading...';
        viewBtn.disabled = true;
    }
    
    try {
        // Get archive metadata
        const archiveDoc = await db.collection('archives').doc(archiveId).get();
        if (!archiveDoc.exists) {
            showMessage('Archive not found.', 'error');
            return;
        }
        
        const archive = archiveDoc.data();
        
        // Get archived students
        const studentsSnapshot = await db.collection('archives')
            .doc(archiveId)
            .collection('students')
            .limit(100)
            .get();
        
        // Create preview modal content
        const date = new Date(archive.timestamp);
        let previewHTML = `
            <div style="position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 1000; display: flex; align-items: center; justify-content: center;">
                <div style="background: white; border-radius: 20px; padding: 30px; max-width: 90%; max-height: 80%; overflow: auto; box-shadow: 0 20px 60px rgba(0,0,0,0.3);">
                    <h2 style="color: #2d3748; margin-bottom: 20px;">Archive Preview</h2>
                    <div style="margin-bottom: 20px;">
                        <strong>Created:</strong> ${date.toLocaleString()}<br>
                        <strong>Description:</strong> ${archive.description}<br>
                        <strong>Total Students:</strong> ${archive.studentCount}
                    </div>
                    <div style="max-height: 400px; overflow-y: auto;">
                        <table class="preview-table">
                            <thead>
                                <tr>
                                    <th>Student ID</th>
                                    <th>First Name</th>
                                    <th>Last Name</th>
                                    <th>Email</th>
                                    <th>Program</th>
                                    <th>Year</th>
                                </tr>
                            </thead>
                            <tbody>
        `;
        
        studentsSnapshot.docs.forEach(doc => {
            const student = doc.data();
            previewHTML += `
                <tr>
                    <td>${student.studentId || ''}</td>
                    <td>${student.firstName || ''}</td>
                    <td>${student.lastName || ''}</td>
                    <td>${student.email || ''}</td>
                    <td>${student.program || ''}</td>
                    <td>${student.year || ''}</td>
                </tr>
            `;
        });
        
        if (archive.studentCount > 100) {
            previewHTML += `
                <tr>
                    <td colspan="6" style="text-align: center; font-style: italic; color: #718096;">
                        ... and ${archive.studentCount - 100} more students
                    </td>
                </tr>
            `;
        }
        
        previewHTML += `
                            </tbody>
                        </table>
                    </div>
                    <div style="text-align: right; margin-top: 20px;">
                        <button class="btn btn-secondary" onclick="closeArchivePreview()">Close</button>
                        <button class="btn btn-primary" style="background: #28a745;" onclick="exportArchive('${archiveId}')">Export as CSV</button>
                    </div>
                </div>
            </div>
        `;
        
        // Create and append preview div
        const previewDiv = document.createElement('div');
        previewDiv.id = 'archive-preview-modal';
        previewDiv.innerHTML = previewHTML;
        document.body.appendChild(previewDiv);
        
    } catch (error) {
        console.error('Error viewing archive:', error);
        showMessage('Error viewing archive: ' + error.message, 'error');
    } finally {
        if (viewBtn) {
            viewBtn.innerHTML = 'View Archive';
            viewBtn.disabled = false;
        }
    }
}

function closeArchivePreview() {
    const modal = document.getElementById('archive-preview-modal');
    if (modal) {
        modal.remove();
    }
}

async function restoreArchive() {
    const archiveId = document.getElementById('archive-select').value;
    if (!archiveId) {
        showMessage('Please select an archive to restore.', 'error');
        return;
    }
    
    await restoreArchiveById(archiveId);
}

async function restoreArchiveById(archiveId) {
    const confirmed = confirm(
        'Are you sure you want to restore this archive?\n\n' +
        'This will:\n' +
        '• Replace ALL current student data\n' +
        '• The current data will be automatically backed up first\n\n' +
        'Continue?'
    );
    
    if (!confirmed) {
        return;
    }
    
    const restoreBtn = document.getElementById('restore-archive-btn');
    if (restoreBtn) {
        restoreBtn.innerHTML = '<span class="loading"></span>Restoring...';
        restoreBtn.disabled = true;
    }
    
    try {
        // First, create a backup of current data
        showMessage('Creating backup of current data...', 'info');
        
        const currentSnapshot = await db.collection('students').get();
        if (!currentSnapshot.empty) {
            // Create automatic backup
            const backupId = `auto_backup_${Date.now()}`;
            const backupData = {
                id: backupId,
                createdAt: firebase.firestore.FieldValue.serverTimestamp(),
                timestamp: Date.now(),
                description: `Automatic backup before restore from ${new Date().toLocaleString()}`,
                studentCount: currentSnapshot.size,
                createdBy: 'System',
                type: 'auto_backup_before_restore'
            };
            
            await db.collection('archives').doc(backupId).set(backupData);
            
            // Backup current students
            const backupBatch = db.batch();
            currentSnapshot.docs.forEach(doc => {
                const backupRef = db.collection('archives')
                    .doc(backupId)
                    .collection('students')
                    .doc(doc.id);
                backupBatch.set(backupRef, {
                    ...doc.data(),
                    archivedAt: Date.now(),
                    originalId: doc.id
                });
            });
            await backupBatch.commit();
        }
        
        showMessage('Restoring archive data...', 'info');
        
        // Get archive metadata
        const archiveDoc = await db.collection('archives').doc(archiveId).get();
        if (!archiveDoc.exists) {
            showMessage('Archive not found.', 'error');
            return;
        }
        
        // Get all archived students
        const archivedStudentsSnapshot = await db.collection('archives')
            .doc(archiveId)
            .collection('students')
            .get();
        
        // Clear current students collection
        const clearBatch = db.batch();
        currentSnapshot.docs.forEach(doc => {
            clearBatch.delete(doc.ref);
        });
        await clearBatch.commit();
        
        // Restore archived students
        const restoreBatch = db.batch();
        let restoredCount = 0;
        
        archivedStudentsSnapshot.docs.forEach(doc => {
            const studentData = doc.data();
            const originalId = studentData.originalId || doc.id;
            
            // Remove archive-specific fields
            delete studentData.archivedAt;
            delete studentData.originalId;
            
            const studentRef = db.collection('students').doc(originalId);
            restoreBatch.set(studentRef, {
                ...studentData,
                restoredAt: firebase.firestore.FieldValue.serverTimestamp(),
                restoredFrom: archiveId
            });
            restoredCount++;
        });
        
        await restoreBatch.commit();
        
        showMessage(`Successfully restored ${restoredCount} student records from archive.`, 'success');
        
        // Update analytics
        await updateAnalytics();
        
        // Reload archive history
        loadArchiveHistory();
        
        // If on the manage tab, reload the student data
        if (document.getElementById('manage-tab').classList.contains('active')) {
            loadStudentData();
        }
        
    } catch (error) {
        console.error('Error restoring archive:', error);
        showMessage('Error restoring archive: ' + error.message, 'error');
    } finally {
        if (restoreBtn) {
            restoreBtn.innerHTML = 'Restore Data';
            restoreBtn.disabled = false;
        }
    }
}

async function deleteArchive(archiveId) {
    const confirmed = confirm(
        'Are you sure you want to delete this archive?\n\n' +
        'This will permanently delete the archive and all its data.\n' +
        'This action cannot be undone!'
    );
    
    if (!confirmed) {
        return;
    }
    
    try {
        showMessage('Deleting archive...', 'info');
        
        // Delete all students in the archive
        const studentsSnapshot = await db.collection('archives')
            .doc(archiveId)
            .collection('students')
            .get();
        
        const batch = db.batch();
        
        studentsSnapshot.docs.forEach(doc => {
            batch.delete(doc.ref);
        });
        
        // Delete the archive metadata
        batch.delete(db.collection('archives').doc(archiveId));
        
        await batch.commit();
        
        showMessage('Archive deleted successfully.', 'success');
        
        // Reload archive history and list
        loadArchiveHistory();
        loadArchivesList();
        
    } catch (error) {
        console.error('Error deleting archive:', error);
        showMessage('Error deleting archive: ' + error.message, 'error');
    }
}

async function exportArchive(archiveId) {
    try {
        // Get archived students
        const studentsSnapshot = await db.collection('archives')
            .doc(archiveId)
            .collection('students')
            .get();
        
        const students = studentsSnapshot.docs.map(doc => doc.data());
        const csvContent = generateCSVFromStudents(students);
        
        const archiveDoc = await db.collection('archives').doc(archiveId).get();
        const archive = archiveDoc.data();
        const date = new Date(archive.timestamp);
        const dateString = `${date.getFullYear()}${(date.getMonth() + 1).toString().padStart(2, '0')}${date.getDate().toString().padStart(2, '0')}`;
        
        downloadCSV(csvContent, `archive_${dateString}_${archive.studentCount}_students.csv`);
        showMessage('Archive exported successfully.', 'success');
        
    } catch (error) {
        console.error('Error exporting archive:', error);
        showMessage('Error exporting archive: ' + error.message, 'error');
    }
}

// Notification System
let notifications = [];
let completedEvents = [];

function checkForCompletedEvents() {
    // Check localStorage for completed events
    const storedEvents = localStorage.getItem('completedEvents');
    if (storedEvents) {
        completedEvents = JSON.parse(storedEvents);
        updateNotificationBadge();
    }
}

function addEventCompletionNotification(eventName, eventNumber) {
    const notification = {
        id: Date.now(),
        type: 'event_complete',
        title: 'Event Completed',
        message: `Event #${eventNumber}: ${eventName} has been marked as complete.`,
        timestamp: new Date().toISOString(),
        read: false
    };
    
    notifications.unshift(notification);
    completedEvents.push(notification);
    localStorage.setItem('completedEvents', JSON.stringify(completedEvents));
    updateNotificationBadge();
    
    // Show toast notification
    showToastNotification(`✅ Event "${eventName}" completed!`);
}

function updateNotificationBadge() {
    const unreadCount = notifications.filter(n => !n.read).length;
    const badge = document.getElementById('notification-count');
    
    if (unreadCount > 0) {
        badge.textContent = unreadCount;
        badge.style.display = 'inline-block';
    } else {
        badge.style.display = 'none';
    }
}

function showToastNotification(message) {
    const toast = document.createElement('div');
    toast.className = 'toast-notification';
    toast.innerHTML = `
        <div style="position: fixed; bottom: 20px; right: 20px; background: #10b981; color: white; padding: 16px 24px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); z-index: 3000; animation: slideInUp 0.3s ease;">
            ${message}
        </div>
    `;
    document.body.appendChild(toast);
    
    setTimeout(() => {
        toast.remove();
    }, 3000);
}

// Modal Functions
function showModal(title, content) {
    document.getElementById('modal-title').textContent = title;
    document.getElementById('modal-body').innerHTML = content;
    document.getElementById('modal-overlay').style.display = 'flex';
}

function closeModal() {
    document.getElementById('modal-overlay').style.display = 'none';
}

function showNotifications() {
    let content = '<div>';
    
    if (notifications.length === 0) {
        content += '<p style="text-align: center; color: #6b7280; padding: 32px;">No notifications yet</p>';
    } else {
        notifications.forEach(notif => {
            const date = new Date(notif.timestamp);
            content += `
                <div class="help-item" style="${!notif.read ? 'background: #eff6ff; border-color: #3b82f6;' : ''}">
                    <div style="display: flex; justify-content: space-between; align-items: start;">
                        <div>
                            <h4>${notif.title}</h4>
                            <p>${notif.message}</p>
                            <small style="color: #9ca3af;">${date.toLocaleString()}</small>
                        </div>
                        ${!notif.read ? '<span style="color: #3b82f6; font-size: 0.75rem; font-weight: 600;">NEW</span>' : ''}
                    </div>
                </div>
            `;
            notif.read = true;
        });
    }
    
    content += '</div>';
    
    showModal('Notifications', content);
    updateNotificationBadge();
}

function showInfo() {
    const content = `
        <div class="info-section">
            <h3>System Information</h3>
            <p><strong>Created By:</strong> Andrew Gregware</p>
            <p><strong>Creation Date:</strong> August 29, 2025</p>
            <p><strong>Last Updated:</strong> ${new Date().toLocaleDateString()}</p>
            <p><strong>Version:</strong> 2.0.0</p>
            <p><strong>Database:</strong> Firebase Firestore</p>
        </div>
        
        <div class="info-section">
            <h3>Features</h3>
            <p>• Student data management with CSV upload</p>
            <p>• Event creation and attendance tracking</p>
            <p>• Archive system for data backup/restore</p>
            <p>• Real-time analytics and reporting</p>
            <p>• QR code scanning integration</p>
        </div>
        
        <div class="info-section">
            <h3>Data Limits</h3>
            <p>• Maximum CSV size: 10MB</p>
            <p>• Student ID format: 9 digits</p>
            <p>• Archive retention: Unlimited</p>
        </div>
    `;
    
    showModal('System Information', content);
}

function showHelp() {
    const content = `
        <div class="help-item">
            <h4>How to Upload Students</h4>
            <p>1. Click "Download CSV Template" to get the correct format</p>
            <p>2. Fill in StudentID, FirstName, LastName, and Email</p>
            <p>3. Drag and drop or click to upload the CSV file</p>
            <p>4. Click "Upload to Database" to import students</p>
        </div>
        
        <div class="help-item">
            <h4>Creating Events</h4>
            <p>1. Go to the Events tab</p>
            <p>2. Click "Create New Event"</p>
            <p>3. Enter a unique event number and name</p>
            <p>4. Events are automatically set to active status</p>
        </div>
        
        <div class="help-item">
            <h4>Managing Archives</h4>
            <p>• Create Archive: Backs up all current student data</p>
            <p>• Restore Archive: Replaces current data with archived version</p>
            <p>• Archives are timestamped and can be exported as CSV</p>
        </div>
        
        <div class="help-item">
            <h4>Keyboard Shortcuts</h4>
            <p><strong>Ctrl + S:</strong> Save current form</p>
            <p><strong>Ctrl + F:</strong> Focus search box</p>
            <p><strong>Esc:</strong> Close modal windows</p>
        </div>
        
        <div class="help-item">
            <h4>Need More Help?</h4>
            <p>Contact support at: agregware@charlestonlaw.edu</p>
            <p>Documentation: docs.insession.com</p>
        </div>
    `;
    
    showModal('Help & Documentation', content);
}

// Add keyboard shortcuts
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        closeModal();
        closeScanDetails();
        closeArchivePreview();
    }
});

// Add animation styles
const styleSheet = document.createElement('style');
styleSheet.textContent = `
    @keyframes slideInUp {
        from {
            transform: translateY(100%);
            opacity: 0;
        }
        to {
            transform: translateY(0);
            opacity: 1;
        }
    }
`;
document.head.appendChild(styleSheet);

// Initialize when page loads
document.addEventListener('DOMContentLoaded', function() {
    // Always initialize the app - auth.js will handle showing login screen if needed
    initializeApp();
    
    // Set up auth check after a brief delay
    setTimeout(() => {
        if (window.isAuthenticated && !window.isAuthenticated()) {
            // User is not authenticated, auth.js should show login screen
            console.log('User not authenticated, login screen should be shown');
        } else if (window.isAuthenticated && window.isAuthenticated()) {
            // User is authenticated, make sure app is fully loaded
            console.log('User authenticated, app ready');
        }
    }, 500);
});

// Initialize the main application
function initializeApp() {
    // Always set up basic UI functionality first
    setupTabNavigation();
    setupDragAndDrop();
    
    // Load data (these will be called after authentication if needed)
    setTimeout(() => {
        if (window.isAuthenticated && window.isAuthenticated()) {
            loadAnalytics();
            loadArchivesList();
            loadArchiveHistory();
            checkForCompletedEvents();
        }
    }, 100);
}

// Set up tab navigation
function setupTabNavigation() {
    // Ensure tabs are clickable
    document.querySelectorAll('.tab').forEach(tab => {
        tab.style.pointerEvents = 'auto';
        tab.style.cursor = 'pointer';
    });
}

// Set up drag and drop
function setupDragAndDrop() {
    const uploadArea = document.querySelector('.upload-area');
    if (uploadArea) {
        uploadArea.addEventListener('dragleave', function(e) {
            e.currentTarget.classList.remove('dragover');
        });
    }
}
