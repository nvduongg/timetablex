import axios from '../utils/axiosConfig';

export const getTimeSlots = () => axios.get('/time-config/slots');
export const createTimeSlot = (data) => axios.post('/time-config/slots', data);
export const updateTimeSlot = (id, data) => axios.put(`/time-config/slots/${id}`, data);
export const deleteTimeSlot = (id) => axios.delete(`/time-config/slots/${id}`);
