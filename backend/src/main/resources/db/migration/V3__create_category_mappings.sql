CREATE TABLE category_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keyword VARCHAR(255) NOT NULL UNIQUE,
    category VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed common merchant-to-category mappings
INSERT INTO category_mappings (keyword, category) VALUES
    -- Coffee & Cafes
    ('STARBUCKS', 'Coffee Shop'),
    ('COSTA COFFEE', 'Coffee Shop'),
    ('DUNKIN', 'Coffee Shop'),
    ('TIM HORTONS', 'Coffee Shop'),
    -- Food & Dining
    ('MCDONALDS', 'Fast Food'),
    ('MCDONALD''S', 'Fast Food'),
    ('KFC', 'Fast Food'),
    ('BURGER KING', 'Fast Food'),
    ('DOMINO''S', 'Fast Food'),
    ('PIZZA HUT', 'Fast Food'),
    ('SUBWAY', 'Fast Food'),
    ('TACO BELL', 'Fast Food'),
    ('WENDY''S', 'Fast Food'),
    ('POPEYES', 'Fast Food'),
    ('CHIPOTLE', 'Fast Food'),
    -- Restaurants
    ('RESTAURANT', 'Dining'),
    ('GRILL', 'Dining'),
    ('KITCHEN', 'Dining'),
    ('SUSHI', 'Dining'),
    ('PHO', 'Dining'),
    -- Groceries
    ('WALMART', 'Groceries'),
    ('TARGET', 'Shopping'),
    ('COSTCO', 'Groceries'),
    ('WHOLE FOODS', 'Groceries'),
    ('TRADER JOE', 'Groceries'),
    ('ALDI', 'Groceries'),
    ('KROGER', 'Groceries'),
    ('SAFEWAY', 'Groceries'),
    ('PUBLIX', 'Groceries'),
    -- Transport
    ('UBER', 'Transportation'),
    ('GRAB', 'Transportation'),
    ('LYFT', 'Transportation'),
    ('TAXI', 'Transportation'),
    ('GAS', 'Transportation'),
    ('SHELL', 'Transportation'),
    ('EXXON', 'Transportation'),
    ('BP', 'Transportation'),
    -- Online Shopping
    ('AMAZON', 'Online Shopping'),
    ('EBAY', 'Online Shopping'),
    ('SHOPIFY', 'Online Shopping'),
    ('ETSY', 'Online Shopping'),
    -- Utilities
    ('ELECTRIC', 'Utilities'),
    ('WATER', 'Utilities'),
    ('INTERNET', 'Utilities'),
    ('PHONE', 'Utilities'),
    ('SPECTRUM', 'Utilities'),
    ('COMCAST', 'Utilities'),
    ('VERIZON', 'Utilities'),
    ('AT&T', 'Utilities'),
    -- Subscriptions
    ('NETFLIX', 'Subscriptions'),
    ('SPOTIFY', 'Subscriptions'),
    ('APPLE', 'Subscriptions'),
    ('GOOGLE', 'Subscriptions'),
    ('MICROSOFT', 'Subscriptions'),
    ('ADOBE', 'Subscriptions'),
    ('DISNEY', 'Subscriptions'),
    ('HULU', 'Subscriptions'),
    -- Health
    ('PHARMACY', 'Health'),
    ('CVS', 'Health'),
    ('WALGREENS', 'Health'),
    ('HOSPITAL', 'Health'),
    ('CLINIC', 'Health'),
    ('DENTIST', 'Health'),
    ('DOCTOR', 'Health'),
    -- Entertainment
    ('CINEMA', 'Entertainment'),
    ('MOVIE', 'Entertainment'),
    ('THEATRE', 'Entertainment'),
    ('CONCERT', 'Entertainment'),
    ('ARCADE', 'Entertainment'),
    -- Travel
    ('HOTEL', 'Travel'),
    ('AIRLINE', 'Travel'),
    ('AIRBNB', 'Travel'),
    ('EXPEDIA', 'Travel'),
    ('BOOKING', 'Travel'),
    ('MARRIOTT', 'Travel'),
    ('HILTON', 'Travel'),
    ('FLIGHT', 'Travel');

CREATE INDEX idx_category_mappings_keyword ON category_mappings(keyword);
