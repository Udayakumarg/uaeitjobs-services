-- Round-2 augmentation of the UAE IT hiring directory.
--
-- Source: WebSearch pass across six categories (Hub71/DIC tenants,
-- cybersecurity, fintech, healthtech, retail/consumer, hospitality,
-- real-estate, IT services, crypto/Web3, payments, gov tech) plus the
-- 10 genuinely-new entries from the original CSV after dedup against V19.
--
-- All URLs are either:
--   - directly observed during the WebSearch pass (url_verified = true), or
--   - constructed from the company's known root domain (url_verified = false
--     so the admin queue surfaces them for human verification).
--
-- ON CONFLICT (slug) DO NOTHING keeps the migration safe to re-run.

INSERT INTO hiring_companies (
    name, slug, category, city, careers_url, website_url,
    hiring_status, status, url_verified, featured, created_at, updated_at
) VALUES
    -- ── IT Services & Consultancy (from CSV — global firms with UAE presence) ──
    ('Cognizant Middle East', 'cognizant-middle-east', 'Consulting & Services', 'Dubai',
     'https://careers.cognizant.com/global/en/locations/united-arab-emirates',
     'https://www.cognizant.com/ae/en', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Capgemini Middle East', 'capgemini-middle-east', 'Consulting & Services', 'Dubai',
     'https://www.capgemini.com/jobs/',
     'https://www.capgemini.com/ae-en/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Infosys Middle East', 'infosys-middle-east', 'Consulting & Services', 'Dubai',
     'https://career.infosys.com/',
     'https://www.infosys.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Tata Consultancy Services (TCS)', 'tata-consultancy-services-tcs', 'Consulting & Services', 'Dubai',
     'https://www.tcs.com/careers',
     'https://www.tcs.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Wipro UAE', 'wipro-uae', 'Consulting & Services', 'Dubai',
     'https://careers.wipro.com/',
     'https://www.wipro.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('HCLTech Middle East', 'hcltech-middle-east', 'Consulting & Services', 'Dubai',
     'https://www.hcltech.com/careers',
     'https://www.hcltech.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),

    -- ── Logistics (from CSV — global logistics with major UAE ops) ──
    ('DHL Express UAE', 'dhl-express-uae', 'Logistics', 'Dubai',
     'https://careers.dhl.com/global/en/middle-east-and-africa-jobs',
     'https://www.dhl.com/ae-en/home.html', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('FedEx Middle East', 'fedex-middle-east', 'Logistics', 'Dubai',
     'https://careers.fedex.com/',
     'https://www.fedex.com/en-ae/home.html', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),

    -- ── Conglomerates / Sovereign-backed (from CSV) ──
    ('EDGE Group', 'edge-group', 'AI & Technology', 'Abu Dhabi',
     'https://www.edgegroup.ae/careers',
     'https://www.edgegroup.ae/', 'ACTIVE_HIRING', 'APPROVED', false, true, now(), now()),
    ('ADQ', 'adq', 'AI & Technology', 'Abu Dhabi',
     'https://www.adq.ae/careers',
     'https://www.adq.ae/', 'OCCASIONAL', 'APPROVED', false, false, now(), now()),

    -- ── Real Estate (from WebSearch) ──
    ('Aldar Properties', 'aldar-properties', 'Real Estate', 'Abu Dhabi',
     'https://www.aldar.com/en/careers',
     'https://www.aldar.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Nakheel', 'nakheel', 'Real Estate', 'Dubai',
     'https://www.nakheel.com/en/about/careers',
     'https://www.nakheel.com/', 'OCCASIONAL', 'APPROVED', false, false, now(), now()),
    ('Sobha Realty', 'sobha-realty', 'Real Estate', 'Dubai',
     'https://www.sobharealty.com/careers/',
     'https://www.sobharealty.com/', 'OCCASIONAL', 'APPROVED', false, false, now(), now()),

    -- ── Hospitality (verified via WebSearch) ──
    ('Jumeirah Group', 'jumeirah-group', 'Hospitality', 'Dubai',
     'https://www.jumeirah.com/en/careers',
     'https://www.jumeirah.com/', 'FREQUENT_HIRING', 'APPROVED', true, false, now(), now()),
    ('Emaar Hospitality Group', 'emaar-hospitality-group', 'Hospitality', 'Dubai',
     'https://www.emaarhospitality.com/en/careers/',
     'https://www.emaarhospitality.com/', 'FREQUENT_HIRING', 'APPROVED', true, false, now(), now()),
    ('Rotana Hotels', 'rotana-hotels', 'Hospitality', 'Abu Dhabi',
     'https://www.rotana.com/careers',
     'https://www.rotana.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Atlantis Resorts Dubai', 'atlantis-resorts-dubai', 'Hospitality', 'Dubai',
     'https://www.atlantis.com/careers',
     'https://www.atlantis.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),

    -- ── Retail & Consumer ──
    ('Sharaf DG', 'sharaf-dg', 'Retail', 'Dubai',
     'https://www.sharafdg.com/about-us/careers/',
     'https://www.sharafdg.com/', 'ACTIVE_HIRING', 'APPROVED', false, false, now(), now()),
    ('Chalhoub Group', 'chalhoub-group', 'Retail', 'Dubai',
     'https://careers.chalhoubgroup.com/',
     'https://www.chalhoubgroup.com/', 'ACTIVE_HIRING', 'APPROVED', true, true, now(), now()),
    ('Lulu Group International', 'lulu-group-international', 'Retail', 'Abu Dhabi',
     'https://careers.lulugroupinternational.com/',
     'https://www.lulugroup.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Apparel Group', 'apparel-group', 'Retail', 'Dubai',
     'https://www.apparelglobal.com/career/',
     'https://www.apparelglobal.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Landmark Group', 'landmark-group', 'Retail', 'Dubai',
     'https://www.landmarkgroup.com/careers',
     'https://www.landmarkgroup.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Dubai Duty Free', 'dubai-duty-free', 'Retail', 'Dubai',
     'https://www.dubaidutyfree.com/careers',
     'https://www.dubaidutyfree.com/', 'OCCASIONAL', 'APPROVED', false, false, now(), now()),

    -- ── Cybersecurity (verified via WebSearch) ──
    ('Help AG', 'help-ag', 'Cybersecurity', 'Dubai',
     'https://www.helpag.com/careers/',
     'https://www.helpag.com/', 'ACTIVE_HIRING', 'APPROVED', true, true, now(), now()),
    ('DTS Solution', 'dts-solution', 'Cybersecurity', 'Dubai',
     'https://www.dts-solution.com/company/careers/',
     'https://www.dts-solution.com/', 'FREQUENT_HIRING', 'APPROVED', true, false, now(), now()),
    ('Spire Solutions', 'spire-solutions', 'Cybersecurity', 'Dubai',
     'https://www.spiresolutions.com/careers/',
     'https://www.spiresolutions.com/', 'FREQUENT_HIRING', 'APPROVED', true, false, now(), now()),

    -- ── Fintech / Startups ──
    ('Pyypl', 'pyypl', 'Banking & FinTech', 'Abu Dhabi',
     'https://pyypl.com/careers',
     'https://pyypl.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Bayzat', 'bayzat', 'Banking & FinTech', 'Dubai',
     'https://bayzat.com/careers',
     'https://bayzat.com/', 'ACTIVE_HIRING', 'APPROVED', false, false, now(), now()),
    ('Sarwa', 'sarwa', 'Banking & FinTech', 'Dubai',
     'https://sarwa.co/careers',
     'https://sarwa.co/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('YAP', 'yap', 'Banking & FinTech', 'Dubai',
     'https://yap.com/ae/en/careers',
     'https://yap.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Foodics', 'foodics', 'Banking & FinTech', 'Dubai',
     'https://www.foodics.com/careers',
     'https://www.foodics.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Lean Technologies', 'lean-technologies', 'Banking & FinTech', 'Dubai',
     'https://www.leantech.me/careers',
     'https://www.leantech.me/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),

    -- ── HealthTech ──
    ('Altibbi', 'altibbi', 'Healthcare', 'Dubai',
     'https://altibbi.com/careers',
     'https://altibbi.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Vezeeta', 'vezeeta', 'Healthcare', 'Dubai',
     'https://www.vezeeta.com/en/careers',
     'https://www.vezeeta.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Okadoc', 'okadoc', 'Healthcare', 'Dubai',
     'https://www.okadoc.com/careers',
     'https://www.okadoc.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Alma Health', 'alma-health', 'Healthcare', 'Dubai',
     'https://www.almahealth.com/careers',
     'https://www.almahealth.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),

    -- ── Payments / Network ──
    ('Network International', 'network-international', 'Banking & FinTech', 'Dubai',
     'https://www.network.ae/careers',
     'https://www.network.ae/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Magnati', 'magnati', 'Banking & FinTech', 'Abu Dhabi',
     'https://www.magnati.com/careers',
     'https://www.magnati.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),

    -- ── Crypto / Web3 ──
    ('Bybit', 'bybit', 'AI & Technology', 'Dubai',
     'https://www.bybit.com/en/about-us/careers',
     'https://www.bybit.com/', 'ACTIVE_HIRING', 'APPROVED', false, true, now(), now()),
    ('Binance Dubai', 'binance-dubai', 'AI & Technology', 'Dubai',
     'https://www.binance.com/en/careers',
     'https://www.binance.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Crypto.com Dubai', 'crypto-com-dubai', 'AI & Technology', 'Dubai',
     'https://crypto.com/careers',
     'https://crypto.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),

    -- ── Specialist IT Services ──
    ('Bluechip Gulf', 'bluechip-gulf', 'Consulting & Services', 'Abu Dhabi',
     'https://www.bluechip-gulf.ae/career/',
     'https://www.bluechip-gulf.ae/', 'FREQUENT_HIRING', 'APPROVED', true, false, now(), now()),
    ('Bespin Global ME', 'bespin-global-me', 'Cloud & DevOps', 'Dubai',
     'https://www.bespinglobal.ae/careers',
     'https://www.bespinglobal.ae/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Khazna Data Centers', 'khazna-data-centers', 'Enterprise IT', 'Abu Dhabi',
     'https://www.khazna.ae/careers/',
     'https://www.khazna.ae/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),

    -- ── Government / Tech-driven entities ──
    ('Technology Innovation Institute (TII)', 'technology-innovation-institute-tii', 'AI & Technology', 'Abu Dhabi',
     'https://www.tii.ae/careers',
     'https://www.tii.ae/', 'ACTIVE_HIRING', 'APPROVED', false, true, now(), now()),
    ('Etihad Rail', 'etihad-rail', 'Government & Smart Cities', 'Abu Dhabi',
     'https://www.etihadrail.ae/careers',
     'https://www.etihadrail.ae/', 'OCCASIONAL', 'APPROVED', false, false, now(), now()),
    ('Roads & Transport Authority (RTA)', 'roads-and-transport-authority-rta', 'Government & Smart Cities', 'Dubai',
     'https://www.rta.ae/wps/portal/rta/ae/home/careers',
     'https://www.rta.ae/', 'OCCASIONAL', 'APPROVED', false, false, now(), now()),
    ('Mubadala Investment Company', 'mubadala-investment-company', 'Banking & FinTech', 'Abu Dhabi',
     'https://www.mubadala.com/en/careers',
     'https://www.mubadala.com/', 'OCCASIONAL', 'APPROVED', false, false, now(), now()),

    -- ── Sustainability / Smart utilities ──
    ('Beeah Group', 'beeah-group', 'Government & Smart Cities', 'Sharjah',
     'https://www.beeahgroup.com/careers',
     'https://www.beeahgroup.com/', 'FREQUENT_HIRING', 'APPROVED', false, false, now(), now()),
    ('Tristar Group', 'tristar-group', 'Logistics', 'Dubai',
     'https://www.tristar-group.co/careers',
     'https://www.tristar-group.co/', 'OCCASIONAL', 'APPROVED', false, false, now(), now())
ON CONFLICT (slug) DO NOTHING;
