import axios from '../utils/axiosConfig';

export const getClassSectionsBySemester = (semesterId, facultyId = null) =>
  axios.get('/class-sections', { params: { semesterId, ...(facultyId && { facultyId }) } });

export const getClassSectionsByOffering = (offeringId) =>
  axios.get(`/class-sections/by-offering/${offeringId}`);

export const generateClassSections = (semesterId, forceRegenerate = false) =>
  axios.post('/class-sections/generate', { semesterId, forceRegenerate });

export const assignLecturerToSection = (sectionId, { lecturerId, skipAssignment }) =>
  axios.put(`/class-sections/${sectionId}/assign`, { lecturerId, skipAssignment });

export const isFacultySkipAllowed = (facultyId) =>
  axios.get('/class-sections/faculty-skip-allowed', { params: { facultyId } });

export const autoAssignLecturers = (semesterId, facultyId = null) =>
  axios.post('/class-sections/auto-assign', { semesterId, facultyId });

export const getTeachingLoad = (semesterId, facultyId = null) =>
  axios.get('/class-sections/teaching-load', { params: { semesterId, ...(facultyId && { facultyId }) } });

export const requestSupport = (sectionId, comment) =>
  axios.put(`/class-sections/${sectionId}/request-support`, { comment });

export const getSupportRequests = (semesterId) =>
  axios.get('/class-sections/support-requests', { params: { semesterId } });

export const fixAdminClasses = (semesterId) =>
  axios.post('/class-sections/fix-admin-classes', { semesterId });
