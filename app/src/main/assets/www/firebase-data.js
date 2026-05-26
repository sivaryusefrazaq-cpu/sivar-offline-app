const SivarFirebase = (() => {
    const localCache = {
        invoices: "invoices",
        invoiceNumber: "invoiceNumber",
        measurementCustomers: "measurementCustomers",
        notesData: "notesData",
        worksData: "worksData"
    };

    const ready = typeof firebase !== "undefined" && firebase.apps && firebase.apps.length > 0;
    const db = ready && firebase.firestore ? firebase.firestore() : null;
    function readLocal(key, fallback){
        const value = localStorage.getItem(key);

        if(value === null){
            return fallback;
        }

        try{
            return JSON.parse(value);
        }catch(error){
            return value;
        }
    }

    function writeLocal(key, value){
        localStorage.setItem(key, typeof value === "string" ? value : JSON.stringify(value));
    }

    async function getAppDoc(id, fallback){
        if(!db){
            return fallback;
        }

        const snapshot = await db.collection("appData").doc(id).get();
        return snapshot.exists ? snapshot.data() : fallback;
    }

    async function setAppDoc(id, data){
        if(!db){
            return;
        }

        await db.collection("appData").doc(id).set({
            ...data,
            updatedAt: firebase.firestore.FieldValue.serverTimestamp()
        }, { merge: true });
    }

    async function getInvoices(){
        const localInvoices = readLocal(localCache.invoices, []);

        try{
            const data = await getAppDoc("invoices", { items: localInvoices });
            const invoices = Array.isArray(data.items) ? data.items : [];
            writeLocal(localCache.invoices, invoices);
            return invoices;
        }catch(error){
            console.error("Could not load invoices from Firebase", error);
            return localInvoices;
        }
    }

    async function saveInvoices(invoices){
        writeLocal(localCache.invoices, invoices);

        try{
            await setAppDoc("invoices", { items: invoices });
        }catch(error){
            console.error("Could not save invoices to Firebase", error);
        }
    }

    async function getNextInvoiceNumber(){
        const localNumber = Number(localStorage.getItem(localCache.invoiceNumber) || 1);

        try{
            const data = await getAppDoc("invoiceCounter", { nextNumber: localNumber });
            const nextNumber = Number(data.nextNumber || 1);
            localStorage.setItem(localCache.invoiceNumber, String(nextNumber));
            return nextNumber;
        }catch(error){
            console.error("Could not load invoice number from Firebase", error);
            return localNumber;
        }
    }

    async function setNextInvoiceNumber(nextNumber){
        localStorage.setItem(localCache.invoiceNumber, String(nextNumber));

        try{
            await setAppDoc("invoiceCounter", { nextNumber });
        }catch(error){
            console.error("Could not save invoice number to Firebase", error);
        }
    }

    async function getMeasurementCustomers(){
        const localCustomers = readLocal(localCache.measurementCustomers, []);

        try{
            const data = await getAppDoc("measurementCustomers", { items: localCustomers });
            const customers = Array.isArray(data.items) ? data.items : [];
            writeLocal(localCache.measurementCustomers, customers);
            return customers;
        }catch(error){
            console.error("Could not load measurements from Firebase", error);
            return localCustomers;
        }
    }

    async function saveMeasurementCustomers(customers){
        writeLocal(localCache.measurementCustomers, customers);

        try{
            await setAppDoc("measurementCustomers", { items: customers });
        }catch(error){
            console.error("Could not save measurements to Firebase", error);
        }
    }

    async function getNotes(){
        const localNotes = readLocal(localCache.notesData, []);

        try{
            const data = await getAppDoc("notes", { items: localNotes });
            const notes = Array.isArray(data.items) ? data.items : [];
            writeLocal(localCache.notesData, notes);
            return notes;
        }catch(error){
            console.error("Could not load notes from Firebase", error);
            return localNotes;
        }
    }

    async function saveNotes(notes){
        writeLocal(localCache.notesData, notes);

        try{
            await setAppDoc("notes", { items: notes });
        }catch(error){
            console.error("Could not save notes to Firebase", error);
        }
    }

    async function getWorks(){
        const localWorks = readLocal(localCache.worksData, []);

        try{
            const data = await getAppDoc("works", { items: localWorks });
            const works = Array.isArray(data.items) ? data.items : [];
            writeLocal(localCache.worksData, works);
            return works;
        }catch(error){
            console.error("Could not load works from Firebase", error);
            return localWorks;
        }
    }

    async function saveWorks(works){
        writeLocal(localCache.worksData, works);

        try{
            await setAppDoc("works", { items: works });
        }catch(error){
            console.error("Could not save works to Firebase", error);
        }
    }


    return {
        ready,
        getInvoices,
        saveInvoices,
        getNextInvoiceNumber,
        setNextInvoiceNumber,
        getMeasurementCustomers,
        saveMeasurementCustomers,
        getNotes,
        saveNotes,
        getWorks,
        saveWorks
    };
})();
