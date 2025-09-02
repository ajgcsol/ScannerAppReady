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
        
        for (const student of csvData.rows) {
            // Validate required fields
            const studentId = student.studentid || student.id || '';
            const firstName = student.firstname || student.first_name || '';
            const lastName = student.lastname || student.last_name || '';
            const email = student.email || '';
            
            if (!studentId || !firstName || !lastName) {
                console.warn('Skipping incomplete record:', student);
                continue;
            }
            
            const studentDoc = {
                studentId: studentId,
                firstName: firstName,
                lastName: lastName,
                email: email,
                program: student.program || '',
                year: student.year || '',
                uploadedAt: firebase.firestore.FieldValue.serverTimestamp(),
                active: true
            };
            
            const docRef = db.collection('students').doc(studentId);
            batch.set(docRef, studentDoc, { merge: true });
            successCount++;
        }
        
        await batch.commit();
        
        showMessage(`Successfully uploaded ${successCount} student records to the database.`, 'success');
        
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
                <td colspan="7" style="text-align: center; padding: 40px; color: #718096;">
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
            <td>${student.program || ''}</td>
            <td>${student.year || ''}</td>
            <td>
                <button class="btn btn-secondary" style="padding: 5px 10px; font-size: 0.9rem;" 
                        onclick="editStudent('${student.id}')">Edit</button>
                <button class="btn" style="padding: 5px 10px; font-size: 0.9rem; background: #fed7d7; color: #742a2a;" 
                        onclick="deleteStudent('${student.id}')">Delete</button>
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
            .orderBy('timestamp', 'desc')
            .limit(10)
            .get();
        
        const activityHTML = recentScans.docs.map(doc => {
            const scan = doc.data();
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
            .orderBy('createdAt', 'desc')
            .get();
        
        const events = eventsSnapshot.docs.map(doc => ({
            id: doc.id,
            ...doc.data()
        }));
        
        displayEvents(events);
        
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
                No events created yet. Click "Create New Event" to get started.
            </div>
        `;
        return;
    }
    
    const eventsHTML = events.map(event => {
        const createdDate = event.createdAt ? new Date(event.createdAt.seconds * 1000).toLocaleDateString() : 'Unknown';
        const isActive = event.isActive ? '✅ Active' : '⏸️ Inactive';
        
        return `
            <div style="background: white; border: 2px solid #e2e8f0; border-radius: 15px; padding: 25px; margin-bottom: 20px;">
                <div style="display: flex; justify-content: between; align-items: center;">
                    <div style="flex: 1;">
                        <div style="display: flex; align-items: center; margin-bottom: 10px;">
                            <h3 style="color: #2d3748; margin-right: 15px;">Event #${event.eventNumber}: ${event.name}</h3>
                            <span style="background: ${event.isActive ? '#c6f6d5' : '#fed7d7'}; color: ${event.isActive ? '#22543d' : '#742a2a'}; padding: 4px 12px; border-radius: 20px; font-size: 0.9rem; font-weight: 600;">${isActive}</span>
                        </div>
                        ${event.description ? `<p style="color: #718096; margin-bottom: 10px;">${event.description}</p>` : ''}
                        <div style="color: #4a5568; font-size: 0.9rem;">
                            <span>Created: ${createdDate}</span>
                        </div>
                    </div>
                    <div style="display: flex; flex-direction: column; gap: 10px;">
                        <button class="btn btn-primary" onclick="viewEventReport('${event.id}')" style="padding: 8px 16px; font-size: 0.9rem;">View Report</button>
                        <button class="btn btn-secondary" onclick="exportEventText('${event.id}')" style="padding: 8px 16px; font-size: 0.9rem;">Export Text</button>
                        <button class="btn btn-secondary" onclick="exportEventXLSX('${event.id}')" style="padding: 8px 16px; font-size: 0.9rem;">Export XLSX</button>
                        <button class="btn btn-secondary" onclick="exportEventErrors('${event.id}')" style="padding: 8px 16px; font-size: 0.9rem;">Export Errors</button>
                        <button class="btn" onclick="toggleEventStatus('${event.id}', ${!event.isActive})" style="padding: 8px 16px; font-size: 0.9rem; background: ${event.isActive ? '#fed7d7' : '#c6f6d5'}; color: ${event.isActive ? '#742a2a' : '#22543d'};">
                            ${event.isActive ? 'Deactivate' : 'Activate'}
                        </button>
                        <button class="btn" onclick="deleteEvent('${event.id}', '${event.name}')" style="padding: 8px 16px; font-size: 0.9rem; background: #e53e3e; color: white; border-color: #e53e3e;">
                            Delete
                        </button>
                    </div>
                </div>
            </div>
        `;
    }).join('');
    
    eventsList.innerHTML = eventsHTML;
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
                .orderBy('timestamp', 'desc')
                .get(),
            // Nested structure (Android app) - use EVENT DOCUMENT ID as listId
            db.collection('lists').doc(eventId)
                .collection('scans')
                .orderBy('timestamp', 'desc')
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
        
        // Get scans for this event from scans collection
        const scansSnapshot = await db.collection('scans')
            .where('eventId', '==', eventId)
            .get();
        
        const scans = scansSnapshot.docs.map(doc => doc.data());
        
        // Generate text delimited format
        const validScans = scans.filter(scan => {
            const cleanId = scan.code.replace(/\s/g, '');
            return cleanId.length === 9;
        });
        
        const textContent = validScans
            .sort((a, b) => a.code.localeCompare(b.code))
            .map(scan => {
                const cleanId = scan.code.replace(/\s/g, '');
                // Format: EventNumber + space + StudentID + "1"
                return `${event.eventNumber} ${cleanId}1`;
            })
            .join('\n');
        
        const today = new Date();
        const dateString = `${(today.getMonth() + 1).toString().padStart(2, '0')}${today.getDate().toString().padStart(2, '0')}${today.getFullYear().toString().substr(2)}`;
        const filename = `Event_${event.eventNumber}_${dateString}.txt`;
        
        downloadTextFile(textContent, filename);
        showMessage(`Exported ${validScans.length} valid scans to ${filename}`, 'success');
        
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
        
        // Get scans for this event
        const scansSnapshot = await db.collection('scans')
            .where('eventId', '==', eventId)
            .get();
        
        const scans = scansSnapshot.docs.map(doc => doc.data());
        
        // Filter for invalid/error records
        const invalidScans = scans.filter(scan => {
            const cleanId = scan.code.replace(/\s/g, '');
            return cleanId.length !== 9 || !scan.verified;
        });
        
        if (invalidScans.length === 0) {
            showMessage('No errors found for this event.', 'info');
            return;
        }
        
        // Generate error content
        const errorContent = invalidScans.map(scan => {
            return `${scan.code} - ${scan.verified ? 'Invalid ID Length' : 'Student Not Found'}`;
        }).join('\n');
        
        const today = new Date();
        const dateString = `${(today.getMonth() + 1).toString().padStart(2, '0')}${today.getDate().toString().padStart(2, '0')}${today.getFullYear().toString().substr(2)}`;
        const filename = `Event_${event.eventNumber}_Errors_${dateString}.txt`;
        
        downloadTextFile(errorContent, filename);
        showMessage(`Exported ${invalidScans.length} error records to ${filename}`, 'success');
        
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

// Placeholder functions for future implementation
function editStudent(studentId) {
    showMessage('Edit functionality coming soon!', 'info');
}

function deleteStudent(studentId) {
    if (confirm('Are you sure you want to delete this student record?')) {
        // Implementation for deleting student
        showMessage('Delete functionality coming soon!', 'info');
    }
}

// Initialize when page loads
document.addEventListener('DOMContentLoaded', function() {
    // Load initial analytics
    loadAnalytics();
    
    // Set up drag and drop for the entire upload area
    const uploadArea = document.querySelector('.upload-area');
    uploadArea.addEventListener('dragleave', function(e) {
        e.currentTarget.classList.remove('dragover');
    });
});