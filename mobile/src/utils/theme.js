import { StyleSheet } from 'react-native';

export const colors = {
  blue:     '#1a56db',
  blueLight:'#e8f0fe',
  green:    '#0ea472',
  red:      '#e02424',
  gray50:   '#f9fafb',
  gray100:  '#f3f4f6',
  gray200:  '#e5e7eb',
  gray500:  '#6b7280',
  gray700:  '#374151',
  gray900:  '#111827',
  white:    '#ffffff',
};

export const globalStyles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.gray50,
  },
  card: {
    backgroundColor: colors.white,
    borderRadius: 12,
    padding: 18,
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: colors.gray900,
    marginBottom: 4,
  },
  cardSub: {
    fontSize: 13,
    color: colors.gray500,
    marginBottom: 16,
    lineHeight: 18,
  },
  label: {
    fontSize: 11,
    fontWeight: '700',
    color: colors.gray700,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 6,
  },
  input: {
    height: 44,
    borderWidth: 1.5,
    borderColor: colors.gray200,
    borderRadius: 8,
    paddingHorizontal: 12,
    fontSize: 15,
    color: colors.gray900,
    backgroundColor: colors.white,
  },
  btn: {
    height: 44,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 20,
  },
  btnPrimary: {
    backgroundColor: colors.blue,
  },
  btnPrimaryText: {
    color: colors.white,
    fontWeight: '700',
    fontSize: 15,
  },
  btnGhost: {
    backgroundColor: colors.gray100,
  },
  btnGhostText: {
    color: colors.gray700,
    fontWeight: '600',
    fontSize: 13,
  },
  btnDanger: {
    backgroundColor: colors.red,
  },
  btnDangerText: {
    color: colors.white,
    fontWeight: '600',
    fontSize: 13,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  spacer: { flex: 1 },
  emptyState: {
    textAlign: 'center',
    color: colors.gray500,
    fontSize: 14,
    paddingVertical: 30,
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 20,
    alignSelf: 'flex-start',
  },
  badgeGreen: { backgroundColor: '#d1fae5' },
  badgeBlue:  { backgroundColor: '#dbeafe' },
  badgeRed:   { backgroundColor: '#fee2e2' },
  badgeGray:  { backgroundColor: colors.gray100 },
  badgeGreenText: { color: '#065f46', fontSize: 11, fontWeight: '700' },
  badgeBlueText:  { color: '#1e40af', fontSize: 11, fontWeight: '700' },
  badgeRedText:   { color: '#991b1b', fontSize: 11, fontWeight: '700' },
  badgeGrayText:  { color: colors.gray700, fontSize: 11, fontWeight: '700' },
});
