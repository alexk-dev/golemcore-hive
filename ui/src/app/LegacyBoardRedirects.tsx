import { Navigate, useParams } from 'react-router-dom';

export function LegacyBoardQueueRedirect() {
  const { boardId = '' } = useParams();
  return <Navigate to={`/services/${boardId}`} replace />;
}

export function LegacyBoardSettingsRedirect() {
  const { boardId = '' } = useParams();
  return <Navigate to={`/services/${boardId}/settings`} replace />;
}
