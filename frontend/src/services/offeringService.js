import axios from '../utils/axiosConfig';

export const getOfferingsBySemester = (semesterId, params = {}) => {
  const p = new URLSearchParams({ semesterId });
  if (params.facultyId != null) p.set('facultyId', params.facultyId);
  if (params.status != null) p.set('status', params.status);
  return axios.get(`/offerings?${p.toString()}`);
};
export const generateAutomatedPlan = (data) => axios.post('/offerings/generate', data);
export const sendForApproval = (semesterId, offeringIds = null) =>
  axios.post('/offerings/send-for-approval', { semesterId, offeringIds });
export const updateOfferingStatus = (id, status, rejectionComment = null) =>
  axios.put(`/offerings/${id}/status`, { status, rejectionComment });
export const updateOfferingPlan = (id, data) =>
  axios.put(`/offerings/${id}`, data);
export const importOfferingsExcel = (semesterId, file) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('semesterId', semesterId);
  return axios.post('/offerings/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } });
};
export const downloadOfferingsTemplate = () =>
  axios.get('/offerings/import/template', { responseType: 'blob' });
