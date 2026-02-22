import axios from '../utils/axiosConfig';

export const generateTimetable = (semesterId) =>
  axios.post('/timetable/generate', { semesterId }, { timeout: 120000 });

export const getTimetable = (semesterId, sectionId = null) =>
  axios.get('/timetable', { params: { semesterId, ...(sectionId && { sectionId }) } });

export const getSuggestions = (entryId) =>
  axios.get(`/timetable/${entryId}/suggestions`);

export const updateTimetableEntry = (entryId, { roomId, shiftId, dayOfWeek }) =>
  axios.put(`/timetable/${entryId}`, { roomId, shiftId, dayOfWeek });

export const confirmTimetable = (semesterId) =>
  axios.post('/timetable/confirm', { semesterId });

export const deleteTimetableEntry = (entryId) =>
  axios.delete(`/timetable/${entryId}`);

export const exportTimetable = (semesterId) =>
  axios.get('/timetable/export', {
    params: { semesterId },
    responseType: 'blob',
    timeout: 30000,
  });

export const getTimetableByAdminClass = (semesterId, adminClassId) =>
  axios.get('/timetable', { params: { semesterId, adminClassId } });

export const assignAdminClassesToSection = (sectionId, adminClassIds) =>
  axios.post(`/timetable/sections/${sectionId}/admin-classes`, { adminClassIds });
