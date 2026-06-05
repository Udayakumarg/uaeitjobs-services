-- Hard whitelist: keep only V19 hand-curated anchor employers + any
-- user-submitted entries. Everything else — including V18 seeds whose
-- careers_url turned out to be an aggregator root (ae.jooble.org,
-- ae.trabajo.ae, talent.ae and many others) — is removed.
--
-- V20 attempted to clean these with a regex of known aggregator hosts,
-- but new aggregators kept surfacing (trabajo, etc.) — a whitelist is
-- the only way to guarantee correctness. Future additions come through
-- /companies/submit (user) or /admin/companies (admin), both of which
-- require an explicit careers URL at submission time.
--
-- Idempotent: re-running this migration on an already-clean DB simply
-- deletes nothing. submitted_by_user_id IS NULL excludes user submissions
-- from the wipe.

DELETE FROM hiring_companies
WHERE submitted_by_user_id IS NULL
  AND slug NOT IN (
    -- Banking & FinTech
    'first-abu-dhabi-bank-fab','emirates-nbd','abu-dhabi-commercial-bank-adcb',
    'mashreq','dubai-islamic-bank-dib','abu-dhabi-islamic-bank-adib','rakbank',
    'commercial-bank-of-dubai-cbd','wio-bank','tabby','tamara',
    -- Telecom
    'e-and-etisalat-group','du',
    -- Aviation & Travel
    'emirates-group','etihad-airways','flydubai','air-arabia','dubai-airports',
    -- AI & Technology (G42 ecosystem)
    'g42','core42','presight','cpx','inception-g42','injazat',
    -- E-commerce & Marketplaces / Real Estate
    'careem','talabat','noon','dubizzle-group','bayut','property-finder','kitopi',
    -- Logistics
    'dp-world','aramex','emirates-post',
    -- Government & Smart Cities
    'tdra','digital-dubai-authority','hub71','dubai-future-foundation','smart-dubai',
    -- Energy & Utilities
    'adnoc','enoc','taqa','dewa',
    -- Healthcare
    'seha','purehealth','aster-dm-healthcare','mediclinic-middle-east',
    -- Real Estate / Conglomerates
    'emaar','majid-al-futtaim','al-futtaim-group','dubai-holding','damac-properties',
    -- Big 4 / Consulting
    'accenture-middle-east','deloitte-middle-east','pwc-middle-east',
    'ey-middle-east','kpmg-lower-gulf',
    -- MNC Tech in UAE
    'microsoft-uae','google-cloud-uae','amazon-web-services-uae',
    'oracle-uae','sap-uae','ibm-uae','cisco-uae','dell-technologies-uae',
    -- Specialist tech recruitment agencies
    'halian','marc-ellis','cooper-fitch','charterhouse-middle-east',
    'black-pearl-consulting','michael-page-middle-east','robert-half-middle-east',
    'hays-middle-east','adecco-middle-east'
  );
